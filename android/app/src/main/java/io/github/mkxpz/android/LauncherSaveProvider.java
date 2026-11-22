package io.github.mkxpz.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Save access for the Fangame Launcher (contract section C).
 *
 * <p>Authority {@code <applicationId>.fangamelauncher}. Every entry point is
 * limited to the launcher package, and only when its signing certificate is in
 * {@code BuildConfig.LAUNCHER_CERT_SHA256}. The save scope is everything under
 * {@code getFilesDir()} except the game data, backups, controls, logs and this
 * provider's staging files (see {@link #isExcluded}).
 *
 * <ul>
 * <li>{@code call("info")}</li>
 * <li>{@code openFile(.../saves.zip, "r")}: ZIP of the save scope, streamed.</li>
 * <li>{@code openFile(.../import.zip, "w")} + {@code call("commit_import")}
 *     / {@code call("discard_import")}: replace the save scope.</li>
 * <li>{@code openFile(.../achievements.jsonl, "r")}</li>
 * </ul>
 */
public final class LauncherSaveProvider extends ContentProvider {

    private static final String TAG = "LauncherSaveProvider";

    private static final int API_VERSION = 1;
    private static final String ACHIEVEMENTS_FILE = "launcher-achievements.jsonl";
    private static final String STAGING_DIR = ".launcher-import";
    private static final String BACKUP_DIR = ".launcher-backup";
    private static final String IMPORT_FILE = "import.zip";
    private static final String EXTRACT_DIR = "extract";
    private static final int KEEP_BACKUPS = 3;
    /** Limits for an imported archive (zip-bomb guard, saves are small). */
    private static final int MAX_ENTRIES = 50_000;
    private static final long MAX_BYTES = 2L << 30;

    /** Shell and root, accepted only when built with mkxpz.allowShellCaller=true. */
    private static final int SHELL_UID = 2000;
    private static final int ROOT_UID = 0;

    /** Serializes imports; the launcher is the only writer. */
    private static final Object LOCK = new Object();

    // ---------------------------------------------------------------- lifecycle

    @Override
    public boolean onCreate() {
        return true;
    }

    // -------------------------------------------------------------- caller check

    /** Throws SecurityException unless the caller is the trusted launcher. */
    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        if (BuildConfig.ALLOW_SHELL_CALLER && (uid == SHELL_UID || uid == ROOT_UID)) return;
        PackageManager pm = requireContext().getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) {
            for (String name : packages) {
                if (LauncherSession.LAUNCHER_PACKAGE.equals(name) && hasAllowedCertificate(pm, name)) return;
            }
        }
        throw new SecurityException("Caller is not the Fangame Launcher");
    }

    @SuppressWarnings("deprecation")
    private static boolean hasAllowedCertificate(PackageManager pm, String name) {
        Set<String> allowed = new HashSet<>();
        for (String hash : BuildConfig.LAUNCHER_CERT_SHA256.split(",")) {
            if (!hash.trim().isEmpty()) allowed.add(hash.trim().toUpperCase(Locale.ROOT));
        }
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                signatures = info == null ? null : info.getApkContentsSigners();
            } else {
                signatures = pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures;
            }
            if (signatures == null || signatures.length == 0) return false;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                if (!allowed.contains(hex(sha256.digest(signature.toByteArray())))) return false;
            }
            return true;
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02X", b));
        return out.toString();
    }

    // --------------------------------------------------------------------- call

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        switch (method) {
            case "info":
                return info();
            case "commit_import":
                return commitImport();
            case "discard_import":
                synchronized (LOCK) {
                    deleteRecursively(stagingDir());
                }
                Bundle done = new Bundle();
                done.putBoolean("accepted", true);
                return done;
            default:
                throw new IllegalArgumentException("Unknown method: " + method);
        }
    }

    private Bundle info() {
        Bundle result = new Bundle();
        boolean running = MkxpActivity.isRunning();
        result.putInt("apiVersion", API_VERSION);
        result.putBoolean("gameRunning", running);
        // The session the game runs under; "" when there is none. The launcher
        // reconciles the sessions whose game_closed was lost (killed process).
        result.putString("sessionId", running ? LauncherSession.activeSessionId() : "");
        result.putString("saveTitle", saveTitle());
        if (BuildConfig.ALLOW_SHELL_CALLER) {
            // Debug builds only: is the installed launcher accepted by the allow-list?
            result.putBoolean("debugLauncherAllowed",
                    hasAllowedCertificate(requireContext().getPackageManager(), LauncherSession.LAUNCHER_PACKAGE));
        }
        try {
            PackageInfo pkg = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            result.putString("versionName", pkg.versionName == null ? "" : pkg.versionName);
            result.putLong("versionCode", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? pkg.getLongVersionCode() : pkg.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            result.putString("versionName", "");
            result.putLong("versionCode", 0);
        }
        return result;
    }

    /** {@code Title=} of {@code Game.ini}, from the installed copy or the APK; "" if missing. */
    private String saveTitle() {
        File installed = new File(new GameDataInstaller(requireContext()).gameDir(), "Game.ini");
        try (InputStream in = installed.isFile() ? new FileInputStream(installed)
                : requireContext().getAssets().open("game/Game.ini")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean inGame = true;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.trim();
                if (line.startsWith("﻿")) line = line.substring(1);
                if (line.startsWith("[")) {
                    inGame = line.equalsIgnoreCase("[Game]");
                } else if (inGame && line.regionMatches(true, 0, "Title", 0, 5)) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase("Title")) {
                        return line.substring(eq + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            // No Game.ini: the title stays empty.
        }
        return "";
    }

    // ----------------------------------------------------------------- openFile

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceCaller();
        String path = uri.getPath() == null ? "" : uri.getPath();
        switch (path) {
            case "/saves.zip":
                if (!"r".equals(mode)) throw new FileNotFoundException("saves.zip is read-only");
                return exportSaves();
            case "/achievements.jsonl":
                if (!"r".equals(mode)) throw new FileNotFoundException("achievements.jsonl is read-only");
                return ParcelFileDescriptor.open(new File(filesDir(), ACHIEVEMENTS_FILE),
                        ParcelFileDescriptor.MODE_READ_ONLY);
            case "/import.zip":
                if (!"w".equals(mode) && !"wt".equals(mode)) throw new FileNotFoundException("import.zip is write-only");
                synchronized (LOCK) {
                    File staging = stagingDir();
                    if (!staging.isDirectory() && !staging.mkdirs()) throw new FileNotFoundException("No staging dir");
                    return ParcelFileDescriptor.open(new File(staging, IMPORT_FILE),
                            ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_TRUNCATE);
                }
            default:
                throw new FileNotFoundException(uri.toString());
        }
    }

    /**
     * Streams a ZIP of the save scope through a pipe.
     *
     * <p>ponytail: files are read as they are; a save written while the game
     * runs may be captured half-way. The launcher can check {@code gameRunning}.
     */
    private ParcelFileDescriptor exportSaves() throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            throw new FileNotFoundException("No pipe: " + e.getMessage());
        }
        final File root = filesDir();
        new Thread(() -> {
            try {
                ZipOutputStream zip = new ZipOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
                byte[] buffer = new byte[64 * 1024];
                for (String relative : listSaveFiles(root)) {
                    File file = new File(root, relative);
                    try (InputStream in = new FileInputStream(file)) {
                        ZipEntry entry = new ZipEntry(relative);
                        entry.setTime(file.lastModified());
                        zip.putNextEntry(entry);
                        int read;
                        while ((read = in.read(buffer)) > 0) zip.write(buffer, 0, read);
                        zip.closeEntry();
                    } catch (FileNotFoundException e) {
                        // Removed while exporting.
                    }
                }
                zip.close();
            } catch (IOException e) {
                Log.w(TAG, "Export failed", e);
                try {
                    pipe[1].closeWithError(e.toString());
                } catch (IOException ignored) {
                    // Already closed.
                }
            }
        }, "launcher-export").start();
        return pipe[0];
    }

    // ------------------------------------------------------------------- import

    private static final class ImportError extends Exception {
        final String code;

        ImportError(String code) {
            super(code);
            this.code = code;
        }
    }

    private static Bundle rejected(String error) {
        Bundle result = new Bundle();
        result.putBoolean("accepted", false);
        result.putString("error", error);
        return result;
    }

    private Bundle commitImport() {
        synchronized (LOCK) {
            if (MkxpActivity.isRunning()) return rejected("game_running");
            File root = filesDir();
            File staging = stagingDir();
            File archive = new File(staging, IMPORT_FILE);
            if (!archive.isFile()) return rejected("no_import");
            File extract = new File(staging, EXTRACT_DIR);
            try {
                deleteRecursively(extract);
                List<String> incoming = extract(archive, extract);
                if (incoming.isEmpty()) throw new ImportError("empty_import");
                swap(root, extract, incoming);
            } catch (ImportError e) {
                deleteRecursively(extract);
                return rejected(e.code);
            } catch (ZipException e) {
                Log.w(TAG, "Invalid archive", e);
                deleteRecursively(extract);
                return rejected("invalid_zip");
            } catch (IOException e) {
                Log.w(TAG, "Import failed", e);
                deleteRecursively(extract);
                return rejected("io_error");
            }
            deleteRecursively(staging);
            Bundle result = new Bundle();
            result.putBoolean("accepted", true);
            return result;
        }
    }

    /**
     * Validates every entry and extracts the ones in the save scope.
     *
     * @return relative paths (with "/") of the extracted files.
     */
    private static List<String> extract(File archive, File destination) throws IOException, ImportError {
        List<String> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        String base = destination.getCanonicalPath() + File.separator;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String relative = safeName(entry.getName(), entry.isDirectory());
                if (++count > MAX_ENTRIES) throw new ImportError("too_large");
                if (entry.isDirectory()) continue;
                if (isExcluded(relative)) {
                    Log.i(TAG, "Skipping excluded entry " + relative);
                    continue;
                }
                if (!seen.add(relative)) throw new ImportError("invalid_entry");
                File out = new File(destination, relative);
                if (!out.getCanonicalPath().startsWith(base)) throw new ImportError("invalid_entry");
                File parent = out.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdirs " + parent);
                try (InputStream in = zip.getInputStream(entry); OutputStream os = new FileOutputStream(out)) {
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > MAX_BYTES) throw new ImportError("too_large");
                        os.write(buffer, 0, read);
                    }
                }
                if (entry.getTime() > 0) out.setLastModified(entry.getTime());
                files.add(relative);
            }
        }
        return files;
    }

    /** Entry name without trailing "/", rejecting absolute paths, "..", "." and empty segments. */
    private static String safeName(String name, boolean directory) throws ImportError {
        if (name.isEmpty() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0 || name.startsWith("/")) {
            throw new ImportError("invalid_entry");
        }
        String trimmed = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String segment : trimmed.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new ImportError("invalid_entry");
        }
        return trimmed;
    }

    /**
     * Moves the current save files to {@code .launcher-backup/<timestamp>/},
     * then moves the extracted files in. Both steps rename whole files and
     * undo themselves on failure.
     */
    private static void swap(File root, File extract, List<String> incoming) throws IOException {
        File backup = new File(new File(root, BACKUP_DIR),
                new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date()));
        List<String> moved = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        try {
            for (String relative : listSaveFiles(root)) {
                moveFile(new File(root, relative), new File(backup, relative));
                moved.add(relative);
            }
            // Directories left behind could collide with an incoming file name.
            pruneEmptyDirs(root, "");
            for (String relative : incoming) {
                moveFile(new File(extract, relative), new File(root, relative));
                placed.add(relative);
            }
        } catch (IOException e) {
            for (String relative : placed) new File(root, relative).delete();
            for (String relative : moved) {
                try {
                    moveFile(new File(backup, relative), new File(root, relative));
                } catch (IOException restore) {
                    Log.e(TAG, "Could not restore " + relative, restore);
                }
            }
            throw e;
        }
        pruneBackups(new File(root, BACKUP_DIR));
    }

    private static void moveFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdirs " + parent);
        if (!from.renameTo(to)) throw new IOException("rename " + from + " -> " + to);
    }

    private static void pruneBackups(File backups) {
        File[] dirs = backups.listFiles(File::isDirectory);
        if (dirs == null) return;
        Arrays.sort(dirs);
        for (int i = 0; i < dirs.length - KEEP_BACKUPS; i++) deleteRecursively(dirs[i]);
    }

    // -------------------------------------------------------------------- scope

    /** True for paths (relative, "/" separated) that are not part of the save scope. */
    static boolean isExcluded(String relative) {
        String[] parts = relative.split("/");
        String first = parts[0];
        if (first.equals("game") || first.startsWith(".launcher-") || first.startsWith(".game-install")) return true;
        String name = parts[parts.length - 1];
        return (name.startsWith("controls_user") && name.endsWith(".json"))
                || name.startsWith("gamepad_map.json")
                || name.endsWith(".log")
                || name.equals("errorlog.txt")
                || name.equals(ACHIEVEMENTS_FILE);
    }

    /** Relative paths of every regular file in the save scope, sorted. */
    private static List<String> listSaveFiles(File root) {
        List<String> files = new ArrayList<>();
        collect(root, "", files);
        Collections.sort(files);
        return files;
    }

    private static void collect(File dir, String prefix, List<String> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String relative = prefix + child.getName();
            if (isExcluded(relative) || Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) collect(child, relative + "/", out);
            else if (child.isFile()) out.add(relative);
        }
    }

    private static void pruneEmptyDirs(File dir, String prefix) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String relative = prefix + child.getName();
            if (!child.isDirectory() || isExcluded(relative) || Files.isSymbolicLink(child.toPath())) continue;
            pruneEmptyDirs(child, relative + "/");
            child.delete(); // only succeeds when empty
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.isDirectory() && !Files.isSymbolicLink(file.toPath()) ? file.listFiles() : null;
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    private File filesDir() {
        return requireContext().getFilesDir();
    }

    private File stagingDir() {
        return new File(filesDir(), STAGING_DIR);
    }

    // -------------------------------------------------- unsupported entry points

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        enforceCaller();
        return null;
    }

    @Override
    public String getType(Uri uri) {
        enforceCaller();
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        enforceCaller();
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        enforceCaller();
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        enforceCaller();
        throw new UnsupportedOperationException();
    }
}
