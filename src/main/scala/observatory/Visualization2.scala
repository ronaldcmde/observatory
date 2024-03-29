package observatory

import com.sksamuel.scrimage.{Image, Pixel, ScaleMethod}

/**
  * 5th milestone: value-added information visualization
  */
object Visualization2 {

  /**
    * @param point (x, y) coordinates of a point in the grid cell
    * @param d00 Top-left value
    * @param d01 Bottom-left value
    * @param d10 Top-right value
    * @param d11 Bottom-right value
    * @return A guess of the value at (x, y) based on the four known values, using bilinear interpolation
    *         See https://en.wikipedia.org/wiki/Bilinear_interpolation#Unit_Square
    */
  def bilinearInterpolation(point: CellPoint, d00: Temperature, d01: Temperature,
                            d10: Temperature, d11: Temperature): Temperature = {
    (d00* (1 - point.x) * (1 - point.y)) +
      (d10 * point.x * (1 - point.y)) +
      (d01 * (1 - point.x) * point.y) +
      (d11 * point.x * point.y)
  }

  /**
    * @param grid Grid to visualize
    * @param colors Color scale to use
    * @param tile Tile coordinates to visualize
    * @return The image of the tile at (x, y, zoom) showing the grid using the given color scale
    */
  def visualizeGrid(grid: GridLocation => Temperature, colors: Iterable[(Temperature, Color)], tile: Tile): Image = {
    val scale = 2
    val width, height = 256 / scale
    val alpha = 127

    val x_0 = tile.x * width
    val y_0 = tile.y * height

    val stream = (for {
      k <- y_0 until y_0 + height
      j <- x_0 until x_0 + width
    } yield (j, k)).toStream.par

    val pixels: Array[Pixel] = (for {
      (x, y) <- stream
      loc = Tile(x, y, tile.zoom + 8).toLocation
      temp = interpolate(grid, loc) // Only difference with Interaction.tile
      color = Visualization.interpolateColor(colors, temp)
    } yield Pixel(color.red, color.green, color.blue, alpha)).toArray

    Image(width, height, pixels, 1).scaleTo(256, 256, ScaleMethod.Bilinear)
  }

  /**
    * @param deviations Grid to visualize with its respective year
    * @param colors Color scale to use
    * @return Unit. Writes the image corresponding to each tile to the file system
    */
  def generateTiles(deviations: (Year, GridLocation => Temperature), colors: Iterable[(Temperature, Color)]): Unit = {
    val zoomLevels = 0 to 3
    for {
      zoom <- zoomLevels.toStream.par
      tiles = for {
        y <- 0 until Math.pow(2, zoom).toInt
        x <- 0 until Math.pow(2, zoom).toInt
      } yield (x, y)
      (x, y) <- tiles.toStream.par
      image = visualizeGrid(deviations._2, colors, Tile(x, y, zoom))
    } yield writeImage(deviations._1, Tile(x, y, zoom), image)
  }

  /**
    * Helper functions for visualizeGrid
    */
  def interpolate(grid: GridLocation => Temperature, location: Location): Temperature = {
    val lat = location.lat.toInt
    val lon = location.lon.toInt
    val d00 = grid(GridLocation(lat, lon))
    val d01 = grid(GridLocation(lat + 1, lon))
    val d10 = grid(GridLocation(lat, lon + 1))
    val d11 = grid(GridLocation(lat + 1, lon + 1))
    val cell = CellPoint(location.lon - lon, location.lat - lat)

    bilinearInterpolation(cell, d00, d01, d10, d11)
  }

  /**
    * Helper functions for generateTiles
    */
  def writeImage(year: Year, tile: Tile, image: Image): Unit = {
    val file: java.io.File =
      new java.io.File(s"target/deviations/$year/${tile.zoom}/${tile.x}-${tile.y}.png")

    if (! file.getParentFile.exists) file.getParentFile.mkdirs

    image output file
  }
}