viewport = Viewport.new
segments = [[-240.0, 8.0, 16], [-112.0, 10.0, 16], [48.0, 12.0, 16], [240.0, 14.0, 16]]
table = []
segments.each do |y_start, y_step, count|
	count.times do |i|
		y = y_start + i * y_step
		width = 0.62 + (y + 240.0) / 720.0 * 0.38
		table.concat([y * 0.82 + 28.0, width, width + 0.16])
	end
end
viewport.perspective_center_x = Graphics.width / 2.0
viewport.perspective_set(segments, table)

tile_width = 48
tile_height = 48
colors = [Color.new(205, 70, 55), Color.new(65, 150, 85), Color.new(65, 105, 195), Color.new(220, 180, 55)]
columns = (Graphics.width / tile_width) + 2
rows = (Graphics.height / tile_height) + 2

rows.times do |row|
	columns.times do |column|
		bitmap = Bitmap.new(tile_width, tile_height)
		bitmap.fill_rect(0, 0, tile_width, tile_height, colors[(row + column) % colors.length])
		bitmap.fill_rect(0, 0, tile_width, 2, Color.new(25, 25, 25))
		bitmap.fill_rect(0, 0, 2, tile_height, Color.new(25, 25, 25))
		sprite = Sprite.new(viewport)
		sprite.bitmap = bitmap
		sprite.x = column * tile_width - tile_width
		sprite.y = row * tile_height - tile_height
		sprite.perspective_mode = 1
		sprite.perspective_slices = 4
	end
end

4.times do |column|
	bitmap = Bitmap.new(32, 96)
	bitmap.fill_rect(0, 0, 32, 96, colors[column])
	bitmap.fill_rect(0, 0, 32, 2, Color.new(255, 255, 255))
	sprite = Sprite.new(viewport)
	sprite.bitmap = bitmap
	sprite.x = Graphics.width / 2 - 72 + column * 48
	sprite.y = Graphics.height / 2 + 40
	sprite.oy = 96
	sprite.perspective_mode = 2
	sprite.perspective_anchor = 0
	sprite.perspective_gain = 1.0
	sprite.perspective_lift = column * 3
end

loop do
	Graphics.update
	Input.update
end
