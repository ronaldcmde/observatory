package observatory

import com.sksamuel.scrimage.{Image, Pixel}

import scala.annotation.tailrec
import scala.language.postfixOps


/**
  * 2nd milestone: basic visualization
  */
object Visualization {

  // For implicit conversions, enables jumping over DF, DS and RDD APIs seamlessly.
  import spark.implicits._

  /**
    * @param temperatures Known temperatures: pairs containing a location and the temperature at this location
    * @param location Location where to predict the temperature
    * @return The predicted temperature at `location`
    *         *         TODO : Implement using spark + akka
    */
  def predictTemperature(temperatures: Iterable[(Location, Temperature)], location: Location): Temperature = {
    val p = 2.5 // Hacer 6
    val errorRange = 1000 // meters

    val weights = temperatures.toStream.par.map {
      case (loc, _) =>
        val d = distance(loc, location)
        if (d > errorRange) weight(p)(d) else 1.0
    }

    val sumOfWeights = weights.sum

    val sumOfWeightedTemps = temperatures.toStream.par.zip(weights).map {
      case ((loc, temp), weight) =>
        val d = distance(loc, location)
        if (d > errorRange) weight * temp else temp
    }.sum

    sumOfWeightedTemps / sumOfWeights
  }

  /**
    * @param points Pairs containing a value and its associated color
    * @param value The value to interpolate
    * @return The color that corresponds to `value`, according to the color scale defined by `points`
    */
  def interpolateColor(points: Iterable[(Temperature, Color)], value: Temperature): Color = {
    def poly(x0: Temperature, y0: Int, x1: Temperature, y1: Int, x: Temperature): Int = {
      if (x0 == x1) y0
      else math.round((y0 * (x1 - x) + y1 * (x - x0)) / (x1 - x0)).toInt
    }

    val sortedPoints = points.toList.sortBy(_._1).reverse

    val (high, low) = indexes(sortedPoints, sortedPoints.head, sortedPoints.head, value)

    Color(
      poly(low._1, low._2.red, high._1, high._2.red, value),
      poly(low._1, low._2.green, high._1, high._2.green, value),
      poly(low._1, low._2.blue, high._1, high._2.blue, value)
    )
  }

  /**
    * @param temperatures Known temperatures
    * @param colors Color scale
    * @return A 360×180 image where each pixel shows the predicted temperature at its location
    *         *         TODO : Implement using spark + akka
    */
  def visualize(temperatures: Iterable[(Location, Temperature)], colors: Iterable[(Temperature, Color)]): Image = {
    val width = 360
    val height = 180
    val alpha = 127

    val pixels: Array[Pixel] = (for {
      i <- Stream.range(0, width * height).par
      loc = index2Location(i, width)
      temp = predictTemperature(temperatures, loc)
      color = interpolateColor(colors, temp)
    } yield Pixel(color.red, color.green, color.blue, alpha)).toArray

    Image(width, height, pixels, 1)
  }

  /**
    * Helper functions for predictTemperature
    */

  def weight(p: Double)(distance: Double): Double = 1 / math.pow(distance, p)

  def distance(x: Location, xi: Location): Double = {
    val delta_lon = math.toRadians(math.abs(x.lon - xi.lon))
    val x_lat = math.toRadians(x.lat)
    val xi_lat = math.toRadians(xi.lat)
    val radius = 6371000.0 // Average Earth radius in meters

    def antipodes(location: Location, location1: Location): Boolean =
      math.toRadians(location.lat) == math.toRadians(location1.lat * -1) && {
        math.toRadians(location1.lon) == {
          if (math.toRadians(location.lon) > 0) math.toRadians(location.lon - 180)
          else math.toRadians(location.lon + 180)
        }
      }

    val omega = {
      if (x_lat == xi_lat) 0
      else if (antipodes(x, xi)) math.Pi
      else
        math.acos(math.sin(x_lat) * math.sin(xi_lat) + math.cos(x_lat) * math.cos(xi_lat) * math.cos(delta_lon))
    }

    radius * omega
  }

  /**
    * Helper functions for interpolateColor
    */

  @tailrec
  def indexes(points: List[(Temperature, Color)], high: (Temperature, Color), low: (Temperature, Color),
                                      value: Temperature): ((Temperature, Color), (Temperature, Color)) = {
    points match {
      case x :: xs =>
        if (x._1 == value) (x, x)
        else if (x._1 < value) (high, x)
        else indexes(xs, x, x, value)
      case Nil => (high, low)
    }
  }

  /**
    * Helper functions for visualize
    */

  def outputImage(image: Image, file: java.io.File): Unit = image output file

  def index2Location(index: Int, rowWidth: Int): Location = {
    val rowIndex: Double = index / rowWidth
    val colIndex: Double = index - (rowIndex * rowWidth)

    val latitude: Double = if (rowIndex <= 90) 90 - rowIndex else (rowIndex - 90) * -1
    val longitude: Double = if (colIndex <= 180) colIndex - 180 else colIndex - 180

    Location(latitude, longitude)
  }
}