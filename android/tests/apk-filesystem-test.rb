require 'tmpdir'
require 'fileutils'

# Host stand-in for the three PhysFS helpers; no engine, game or device needed.
module MKXPZ_APKFS
  ASSETS = {
    'Graphics/Pictures/Hero.png' => "hero\x00data".b,
    'Graphics/Pictures/Nested/Tile.png' => 'tile'.b,
    'Graphics/Pictures/Empty.bin' => ''.b,
    'Graphics/Pictures/.hidden.png' => 'hidden'.b,
    'Audio/BGM/Theme.ogg' => 'music'.b,
    'Graphics/Space name.txt' => 'space'.b
  }.freeze

  def self.native_stat(path)
    disk = File.stat(path.empty? ? '.' : path)
    [disk.ftype.to_sym, disk.size]
  rescue Errno::ENOENT
    asset = ASSETS.keys.find { |name| name.casecmp?(path) }
    return [:file, ASSETS[asset].bytesize] if asset
    prefix = path.empty? ? '' : path + '/'
    [:directory, 0] if ASSETS.keys.any? { |name| name.downcase.start_with?(prefix.downcase) }
  end

  def self.native_read(path)
    asset = ASSETS.keys.find { |name| name.casecmp?(path) }
    raise Errno::EISDIR, path if native_stat(path)&.first == :directory
    raise Errno::ENOENT, path unless asset
    ASSETS.fetch(asset).dup
  end

  def self.native_entries(path)
    raise Errno::ENOENT, path unless native_stat(path)
    raise Errno::ENOTDIR, path unless native_stat(path).first == :directory
    prefix = path.empty? ? '' : path + '/'
    virtual = ASSETS.keys.filter_map do |name|
      name[prefix.length..].split('/').first if name.downcase.start_with?(prefix.downcase)
    end
    disk = Dir._ZBOX_apk_orig_children(path.empty? ? '.' : path)
    (disk + virtual).uniq.sort
  rescue Errno::ENOENT
    virtual || raise
  end
end

TEST_ROOT = Dir.mktmpdir('mkxpz-apk-ruby-')
Dir.chdir(TEST_ROOT)
FileUtils.mkdir_p('Data')
at_exit do
  Dir.chdir('/')
  FileUtils.remove_entry(TEST_ROOT)
end
load File.expand_path('../../binding/android-apk-filesystem.rb', __dir__)

class ApkFilesystemTest
  def assert(value)
    raise 'Assertion failed' unless value
  end

  def refute(value)
    assert(!value)
  end

  def assert_equal(expected, actual)
    raise "Expected #{expected.inspect}, got #{actual.inspect}" unless expected == actual
  end

  def assert_nil(value)
    assert_equal(nil, value)
  end

  def assert_empty(value)
    assert(value.empty?)
  end

  def assert_includes(values, value)
    assert(values.include?(value))
  end

  def assert_raises(error)
    begin
      yield
    rescue error
      return
    end
    raise "Expected #{error}"
  end

  def teardown
    FileUtils.rm_rf('Graphics')
    FileUtils.rm_f('saved.dat')
  end

  def test_existence_types_case_and_size
    [File, FileTest].each do |target|
      assert target.exist?('Graphics/Pictures/Hero.png')
      assert target.exists?('./graphics/pictures/hero.PNG')
      assert target.file?('Graphics/Pictures/Empty.bin')
      assert target.directory?('graphics/pictures')
      refute target.file?('Graphics/Pictures')
      refute target.directory?('Graphics/Pictures/Hero.png')
      refute target.exist?('Graphics/missing')
    end
    assert Dir.exist?('Audio/BGM')
    assert Dir.exists?('Audio')
    assert_equal 0, File.size('Graphics/Pictures/Empty.bin')
    assert_equal 9, File.size('Graphics/Pictures/Hero.png')
  end

  def test_read_open_seek_encoding_and_block_close
    assert_equal "hero\x00data".b, File.binread('Graphics/Pictures/Hero.png')
    assert_equal 'ero', File.read('Graphics/Pictures/Hero.png', 3, 1)
    assert_equal 'ero'.b, File.binread('Graphics/Pictures/Hero.png', 3, 1)
    assert_equal '', File.read('Graphics/Pictures/Empty.bin')
    assert_nil File.binread('Graphics/Pictures/Empty.bin', 1)
    assert_equal Encoding.default_external, File.read('Graphics/Space name.txt').encoding
    assert_equal Encoding::BINARY, File.binread('Graphics/Space name.txt').encoding
    stream = nil
    result = File.open('Graphics/Pictures/Hero.png', 'rb') do |reader|
      stream = reader
      assert_equal 'Graphics/Pictures/Hero.png', reader.path
      assert_equal 'he'.b, reader.read(2)
      reader.seek(-4, IO::SEEK_END)
      assert_equal 'data'.b, reader.read
      assert reader.eof?
      assert_raises(IOError) { reader.write('no') }
      :returned
    end
    assert_equal :returned, result
    assert stream.closed?
    File.open('Graphics/Space name.txt', mode: 'r', encoding: 'UTF-8') do |reader|
      assert_equal Encoding::UTF_8, reader.external_encoding
    end
    reader = File.open('Graphics/Space name.txt', File::RDONLY)
    assert_equal 'space', reader.read
    reader.close
  end

  def test_disk_wins_and_writes_stay_on_disk
    Dir.mkdir('Graphics')
    Dir.mkdir('Graphics/Pictures')
    File.write('Graphics/Pictures/Hero.png', 'disk')
    assert_equal 'disk', File.read('Graphics/Pictures/Hero.png')
    assert_equal 4, File.size('Graphics/Pictures/Hero.png')
    File.open('saved.dat', 'wb') { |reader| reader.write('saved') }
    assert_equal 'saved', File.read('saved.dat')
    assert_raises(Errno::ENOENT) { File.open('Graphics/missing/asset', 'wb') }
    assert_raises(Errno::ENOENT) { File.open('Graphics/Space name.txt', 'r+') }
    calls = 0
    assert_raises(Errno::ENOENT) do
      File.open('Graphics/Pictures/Hero.png') do
        calls += 1
        raise Errno::ENOENT, 'from inside the block'
      end
    end
    assert_equal 1, calls
  end

  def test_absolute_paths_escapes_and_cwd
    refute File.exist?('')
    refute Dir.exist?('')
    assert_empty Dir.glob('')
    assert_raises(Errno::ENOENT) { Dir.entries('') }
    assert_raises(Errno::ENOENT) { File.read('') }
    refute File.exist?(TEST_ROOT + '/Graphics/Pictures/Hero.png')
    assert_raises(Errno::ENOENT) { File.read(TEST_ROOT + '/Graphics/Pictures/Hero.png') }
    refute File.exist?('../Graphics/Pictures/Hero.png')
    assert_empty Dir.glob(TEST_ROOT + '/Graphics/**/*')
    assert_empty Dir.glob('../Graphics/**/*')
    Dir.chdir('Data') do
      assert_equal 'music', File.read('../Audio/BGM/Theme.ogg')
      assert_equal ['../Audio/BGM/Theme.ogg'], Dir.glob('../Audio/**/*.ogg')
    end
  end

  def test_disk_file_hides_virtual_directory
    Dir.mkdir('Graphics')
    Dir.mkdir('Graphics/Pictures')
    File.write('Graphics/Pictures/Nested', 'not a directory')
    refute File.exist?('Graphics/Pictures/Nested/Tile.png')
    assert File.file?('Graphics/Pictures/Nested')
    assert_empty Dir.glob('Graphics/Pictures/Nested/*.png')
    assert_empty Dir.glob('Graphics/Pictures/Nested/Tile.png')
    refute Dir.glob('Graphics/**/*.png').include?('Graphics/Pictures/Nested/Tile.png')
    assert_raises(Errno::ENOTDIR) { File.read('Graphics/Pictures/Nested/Tile.png') }
  end

  def test_merged_entries_children_and_foreach
    Dir.mkdir('Graphics')
    Dir.mkdir('Graphics/Pictures')
    File.write('Graphics/Pictures/Extra.png', 'extra')
    expected = %w[.hidden.png Empty.bin Extra.png Hero.png Nested]
    assert_equal expected, Dir.children('Graphics/Pictures').sort
    assert_equal ['.', '..'] + expected, Dir.entries('Graphics/Pictures').sort
    assert_equal expected, Dir.foreach('Graphics/Pictures').to_a.reject { |name| ['.', '..'].include?(name) }.sort
    yielded = []
    assert_nil Dir.foreach('Graphics/Pictures') { |name| yielded << name }
    assert_includes yielded, 'Hero.png'
    assert_raises(Errno::ENOENT) { Dir.entries('missing') }
    assert_raises(Errno::ENOTDIR) { Dir.children('Graphics/Pictures/Hero.png') }
  end

  def test_globs_wildcards_braces_bases_flags_and_blocks
    assert_equal ['Graphics/Pictures/Hero.png'], Dir.glob('Graphics/Pictures/H?ro.*')
    assert_equal ['Graphics/Pictures/Hero.png', 'Graphics/Pictures/Nested/Tile.png'], Dir['Graphics/**/*.png']
    assert_equal ['Audio/BGM/Theme.ogg', 'Graphics/Pictures/Hero.png'],
                 Dir.glob('{Audio/BGM/*.ogg,Graphics/Pictures/Hero.png}')
    assert_equal ['Hero.png'], Dir.glob('*.png', base: 'Graphics/Pictures')
    assert_equal ['Graphics/'], Dir.glob('Graphics/')
    assert_equal ['Graphics/', 'Graphics/Pictures/', 'Graphics/Pictures/Nested/'], Dir.glob('Graphics/**/')
    assert_equal ['Graphics/Pictures/Hero.png'], Dir.glob('Graphics/Pictures/Hero.png')
    assert_includes Dir.glob('Graphics/**/*.png', flags: File::FNM_DOTMATCH), 'Graphics/Pictures/.hidden.png'
    assert_equal ['Hero.png'], Dir['H*.png', base: 'Graphics/Pictures', sort: false]
    yielded = []
    assert_nil Dir.glob('Audio/**/*.ogg') { |name| yielded << name }
    assert_equal ['Audio/BGM/Theme.ogg'], yielded
  end
end

ApkFilesystemTest.instance_methods(false).grep(/^test_/).sort.each do |method|
  test = ApkFilesystemTest.new
  begin
    test.public_send(method)
    puts "PASS #{method}"
  ensure
    test.teardown
  end
end
