module MKXPZ_MapScans
  def self.with_region_tiles(map)
    return yield unless map.respond_to?(:_ZBOX_mkscan_orig_ms_each_region_event_tile, true) &&
                        MakerStudio.respond_to?(:region_tile)
    previous = map.instance_variable_get(:@_mkxpz_region_tiles)
    begin
      # ponytail: snapshot only for a synchronous scan; rebuild if events change between scans.
      tiles = []
      map.events&.each_value do |event|
        tile = MakerStudio.region_tile(event)
        tiles << [event, tile] if tile
      end
      map.instance_variable_set(:@_mkxpz_region_tiles, tiles)
      yield
    ensure
      map.instance_variable_set(:@_mkxpz_region_tiles, previous)
    end
  end

  def self.install_map(target)
    original = :_ZBOX_mkscan_orig_ms_each_region_event_tile
    return false unless target.private_method_defined?(:ms_each_region_event_tile)
    return true if target.private_method_defined?(original)
    target.class_eval do
      alias_method :_ZBOX_mkscan_orig_ms_each_region_event_tile, :ms_each_region_event_tile
      def ms_each_region_event_tile(x, y, &block)
        return _ZBOX_mkscan_orig_ms_each_region_event_tile(x, y, &block) unless @_mkxpz_region_tiles
        @_mkxpz_region_tiles.each do |event, tile|
          next unless event.at_coordinate?(x, y)
          block.call(tile[0], tile[1])
        end
        @events
      end
      private :ms_each_region_event_tile
    end
    true
  end

  def self.install_scan(target)
    original = :_ZBOX_mkscan_orig_scan
    return false unless target.private_method_defined?(:scan) && target.instance_method(:scan).arity == 1
    return true if target.private_method_defined?(original)
    target.class_eval do
      alias_method :_ZBOX_mkscan_orig_scan, :scan
      def scan(map)
        MKXPZ_MapScans.with_region_tiles(map) { _ZBOX_mkscan_orig_scan(map) }
      end
      private :scan
    end
    true
  end
end

mkxpz_map_scan_targets = ['Game_Map', 'Visual25D::WallField', 'Visual25D::InteriorWallField']
mkxpz_map_scan_hook = TracePoint.new(:end) do |event|
  target = event.self
  next unless target.is_a?(Class) && mkxpz_map_scan_targets.include?(target.name)
  installed = target.name == 'Game_Map' ? MKXPZ_MapScans.install_map(target) : MKXPZ_MapScans.install_scan(target)
  next unless installed
  mkxpz_map_scan_targets.delete(target.name)
  mkxpz_map_scan_hook.disable if mkxpz_map_scan_targets.empty?
end
mkxpz_map_scan_hook.enable
