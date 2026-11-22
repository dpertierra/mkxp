tile_width = 64
tile_height = 48
columns = 8
left = (Graphics.width - tile_width * columns) / 2
vanishing_x = Graphics.width / 2

rows = [Graphics.height / 3, Graphics.height * 2 / 3]
rows.each do |y|
	columns.times do |column|
		bitmap = Bitmap.new(tile_width, tile_height)
		color = column.even? ? Color.new(220, 80, 50) : Color.new(245, 190, 70)
		bitmap.fill_rect(0, 0, tile_width, tile_height, color)
		bitmap.fill_rect(0, tile_height - 2, tile_width, 2, Color.new(30, 30, 30))

		sprite = Sprite.new
		sprite.bitmap = bitmap
		sprite.x = left + column * tile_width
		sprite.y = y
		sprite.taper = 0.8
		sprite.taper_center = vanishing_x
	end
end

loop {
	Graphics.update
	Input.update
}
