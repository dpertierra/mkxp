require 'stringio'

# Only evaluated on Android after the APK has been mounted at assets/game.
module MKXPZ_APKFS
  ROOT = Dir.pwd.freeze

  class Reader < StringIO
    attr_reader :path

    def initialize(data, path, mode, **options)
      @path = path
      super(data, mode, **options)
    end
  end

  class << self
    def relative(path, base = Dir.pwd)
      path = File.path(path)
      return nil if path.empty? || path.start_with?('/', '\\') || path.match?(/\A[A-Za-z]:/)
      expanded = File.expand_path(path.tr('\\', '/'), base)
      return '' if expanded == ROOT
      expanded.delete_prefix(ROOT + '/') if expanded.start_with?(ROOT + '/')
    end

    def stat(path)
      disk = File.stat(path)
      [disk.ftype.to_sym, disk.size]
    rescue Errno::ENOENT
      virtual = relative(path)
      native_stat(virtual) if virtual
    rescue Errno::ENOTDIR, Errno::EACCES
      nil
    end

    def readable_mode?(mode)
      if mode.is_a?(Integer)
        (mode & (File::WRONLY | File::RDWR | File::CREAT | File::TRUNC | File::APPEND)).zero?
      else
        mode = String(mode)
        mode.start_with?('r') && !mode.include?('+')
      end
    end

    def open(path, mode, **options)
      virtual = relative(path)
      raise Errno::ENOENT, File.path(path) unless virtual
      # ponytail: File.open buffers one asset; engine audio keeps streaming via PhysFS.
      # Add a streaming Ruby IO binding if plugins start reading large media here.
      binary = mode.is_a?(Integer) ? (mode & File::BINARY) != 0 : mode.start_with?('rb')
      unless binary || options[:binmode] || options[:encoding] || options[:external_encoding] ||
             (mode.is_a?(String) && mode.include?(':'))
        options[:encoding] = Encoding.default_external
      end
      Reader.new(native_read(virtual), File.path(path), mode, **options)
    end

    def children(path, **options)
      virtual = relative(path)
      return Dir._ZBOX_apk_orig_children(path, **options) unless virtual
      disk = begin
        Dir._ZBOX_apk_orig_children(path, **options)
      rescue Errno::ENOENT
        []
      end
      names = native_entries(virtual)
      names.map! { |name| name.encode(options[:encoding]) } if options[:encoding]
      (disk + names).uniq
    end

    def glob(pattern, flags, base:, sort:)
      Array(pattern).flat_map do |item|
        item = File.path(item)
        disk = Dir._ZBOX_apk_orig_glob(item, flags, base: base, sort: sort)
        next disk unless relative(item, base || Dir.pwd)
        unless item.match?(/[*?\[\{]/)
          type = begin
            File.stat(File.expand_path(item, base || Dir.pwd)).ftype.to_sym
          rescue Errno::ENOENT
            native_stat(relative(item, base || Dir.pwd))&.first
          rescue Errno::ENOTDIR
            nil
          end
          next disk unless type && (!item.end_with?('/') || type == :directory)
          next (disk + [item]).uniq
        end

        # ponytail: enumerate each literal-prefix subtree; index it if globs become hot.
        prefix = item.split('/').take_while { |part| part !~ /[*?\[\{]/ }.join('/')
        prefix = File.dirname(prefix) unless item.end_with?('/') || prefix != item
        prefix = '' if prefix == '.'
        root = relative(prefix.empty? ? '.' : prefix, base || Dir.pwd)
        next disk unless root
        physical_root = File.expand_path(prefix.empty? ? '.' : prefix, base || Dir.pwd)
        directory_root = begin
          File.stat(physical_root).directory?
        rescue Errno::ENOENT
          native_stat(root)&.first == :directory
        rescue Errno::ENOTDIR
          false
        end
        next disk unless directory_root

        match_flags = flags | File::FNM_PATHNAME | File::FNM_EXTGLOB
        results = []
        if !prefix.empty? && item.end_with?('/') && File.fnmatch(item, prefix + '/', match_flags)
          results << prefix + '/'
        end
        pending = [[prefix, root]]
        until pending.empty?
          display, directory = pending.pop
          native_entries(directory).each do |name|
            child = display.empty? ? name : "#{display}/#{name}"
            full = directory.empty? ? name : "#{directory}/#{name}"
            physical = File.expand_path(child, base || Dir.pwd)
            type = begin
              File.stat(physical).directory? ? :directory : :file
            rescue Errno::ENOENT
              native_stat(full)&.first
            end
            candidate = item.end_with?('/') && type == :directory ? child + '/' : child
            results << candidate if File.fnmatch(item, candidate, match_flags)
            pending << [child, full] if type == :directory && !File.symlink?(physical)
          end
        end
        merged = (disk + results).uniq
        sort ? merged.sort : merged
      end
    end
  end
end

[File.singleton_class, FileTest.singleton_class].each do |target|
  { exist?: nil, exists?: nil, file?: :file, directory?: :directory }.each do |method, type|
    original = target.method_defined?(method) ? method : :_ZBOX_apk_orig_exist?
    target.alias_method "_ZBOX_apk_orig_#{method}", original
    target.define_method(method) do |path|
      disk = send("_ZBOX_apk_orig_#{method}", path)
      next disk if disk || File._ZBOX_apk_orig_exist?(path)
      result = MKXPZ_APKFS.stat(path)
      type ? result&.first == type : !result.nil?
    end
  end
end

class << File
  alias_method :_ZBOX_apk_orig_size, :size
  def size(path)
    _ZBOX_apk_orig_size(path)
  rescue Errno::ENOENT
    result = MKXPZ_APKFS.stat(path)
    raise unless result
    result[1]
  end

  alias_method :_ZBOX_apk_orig_open, :open
  def open(path, *arguments, **options, &block)
    reader = begin
      _ZBOX_apk_orig_open(path, *arguments, **options)
    rescue Errno::ENOENT
      mode = options.fetch(:mode, arguments.first || 'r')
      raise unless arguments.length <= 1 && MKXPZ_APKFS.readable_mode?(mode)
      MKXPZ_APKFS.open(path, mode, **options.reject { |key, _| key == :mode })
    end
    return reader unless block
    begin
      block.call(reader)
    ensure
      reader.close unless reader.closed?
    end
  end

  alias_method :_ZBOX_apk_orig_read, :read
  def read(path, *arguments, **options)
    _ZBOX_apk_orig_read(path, *arguments, **options)
  rescue Errno::ENOENT
    open(path, **options) do |reader|
      reader.seek(arguments[1]) if arguments[1]
      reader.read(arguments[0])
    end
  end

  alias_method :_ZBOX_apk_orig_binread, :binread
  def binread(path, *arguments)
    _ZBOX_apk_orig_binread(path, *arguments)
  rescue Errno::ENOENT
    open(path, 'rb') do |reader|
      reader.seek(arguments[1]) if arguments[1]
      reader.read(arguments[0])
    end
  end
end

class << Dir
  alias_method :_ZBOX_apk_orig_exist?, :exist?
  def exist?(path)
    File.directory?(path)
  end
  alias_method :exists?, :exist?

  alias_method :_ZBOX_apk_orig_children, :children
  def children(path, **options)
    MKXPZ_APKFS.children(path, **options)
  end

  alias_method :_ZBOX_apk_orig_entries, :entries
  def entries(path, **options)
    ['.', '..'] + children(path, **options)
  end

  alias_method :_ZBOX_apk_orig_foreach, :foreach
  def foreach(path, **options, &block)
    return enum_for(__method__, path, **options) unless block
    entries(path, **options).each(&block)
    nil
  end

  alias_method :_ZBOX_apk_orig_glob, :glob
  def glob(pattern, flags = 0, base: nil, sort: true, **options, &block)
    flags = options.fetch(:flags, flags)
    result = MKXPZ_APKFS.glob(pattern, flags, base: base, sort: sort)
    return result unless block
    result.each(&block)
    nil
  end

  alias_method :_ZBOX_apk_orig_brackets, :[]
  def [](*patterns, **options)
    glob(patterns, **options)
  end
end
