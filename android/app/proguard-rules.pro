# SDL2's Java glue is fetched at build time and talks to the native library
# through JNI; keep its class and member names intact.
-keep class org.libsdl.app.** { *; }

# Our own glue.
-keep class io.github.mkxpz.android.** { *; }
