import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.floor
import kotlin.math.sqrt

/**
 *
 *          ________            ░░              ▒▒   ░░      ▒▒      ░░  ░░
 *         /_  __/ /  ___                 ▒▒      ░░
 *          / / / _ \/ -_)    ░░    ░░        ▒▒    ░░                 ▒▒
 *         /_/ /_//_/\__/    ▒▒              ▒▒▒▒             ░░       ▒▒
 *             ______               __  ___           __    _
 *            / ____/___  ____ _   /  |/  /___ ______/ /_  (_)___  ___
 *           / /_  / __ \/ __ `/  / /|_/ / __ `/ ___/ __ \/ / __ \/ _ \
 *          / __/ / /_/ / /_/ /  / /  / / /_/ / /__/ / / / / / / /  __/
 *         /_/    \____/\__, /  /_/  /_/\__,_/\___/_/ /_/_/_/ /_/\___/
 *                     /____/
* The Fog Machine can generate a GeoJSON files consisting of either:
*
*  - 1 (one) Zone: A collection of square ''Tyle'' Polygons of (numerically) equal Decimal Degrees (DD) side lengths
 *  that form a uniform grid.
 *
*   Or
 *
*  - 1 (one) Myst: A single MultiPolygon that completely covers Earth save for any Zone ''holes'' that are specified.
*
* This generator supports Polygons as small as 0.0001 DD and as large as 1 DD. Generation parameters are designed
 * around the concept of ''Tyles' and ''Zones'. Tyles are the individual Polygons that comprise a Zone.
 * All Tyles in a Zone are given respective properties, allowing specific and localized modifications.
* Zones are the collection of Tyles. These are what are used to create holes in the Myst GeoJSON.
*
* GeoJSON generation is conducted through the use of two DSLs, [ZoneBuilder] and [MystBuilder].
*/
private class FogMachine {
    /**
     * Entry for Zone built from [ZoneBuilder].
     */
    constructor(zone: FogType.Zone) {
        validatePath(zone.path)
        try {
            generateZoneFog(zone)
        }
        finally {
            bufferedWriter.close()
        }

    }
    /**
     * Entry for Myst built from [MystBuilder]
     */
    constructor(myst: FogType.Myst) {
        validatePath(myst.path)
        try {
            generateMystFog(myst)
        }
        finally {
            bufferedWriter.close()
        }
    }

    private lateinit var bottomLeft: Pair<BigDecimal, BigDecimal>
    private lateinit var bottomRight: Pair<BigDecimal, BigDecimal>
    private lateinit var topRight: Pair<BigDecimal, BigDecimal>
    private lateinit var topLeft: Pair<BigDecimal, BigDecimal>
    private lateinit var geoJSONFile : File
    private lateinit var bufferedWriter : BufferedWriter

    /**
     * Validates the [path], including the name and directory, of the GeoJSON file to be generated.
     * Ensures the file can be created and written to. Existing files will be overwritten.
     * @param path A [String] denoting the filename and directory (Ex. `/path/to/file/fog.geojson`)
     * @throws FogMachineException If access was denied to create or write to GeoJSON file at the specified [path].
     */
    private fun validatePath(path: String) {
        geoJSONFile = File(path)
        if (geoJSONFile.parentFile != null) geoJSONFile.parentFile.mkdirs()

        if (geoJSONFile.exists() && !geoJSONFile.canWrite())
            throw FogMachineException("Error: Access denied to write file at $path")

        try {
            bufferedWriter = File(path).bufferedWriter()
        }
        catch (fnf : FileNotFoundException) {
            throw FogMachineException("Error: Access denied to create file at $path")
        }
    }
    /**
     * Perform GeoJSON generation on the given [zone]. Each Tyle is given a
     * sequential ID, starting from the bottom left of the Zone.
     * @param zone The built [ZoneBuilder] object containing all the necessary parameters to create a GeoJSON file.
     * @throws FogMachineException If error occurs while generating GeoJSON.
     */
    private fun generateZoneFog(zone: FogType.Zone) {
        var first = true
        var id = 0
        try {
            // Initial
            bufferedWriter.append("{\"type\":\"FeatureCollection\",\"features\":[")

            // Data
            var lat = BigDecimal(zone.latitude.toString())
            while (lat < zone.latitude.add(zone.zoneCoarseness)) {
                var long = BigDecimal(zone.longitude.toString())
                while (long < zone.longitude.add(zone.zoneCoarseness)) {
                        // Skip the comma for the first Tyle
                        if (first) first = false else bufferedWriter.append(",")
                        bufferedWriter.append("{\"type\":\"Feature\",\"id\":\""
                                        + id++ + "\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[")
                        bottomRight = Pair(long.add(zone.tyleCoarseness), lat)
                        topRight = Pair(long.add(zone.tyleCoarseness), lat.add(zone.tyleCoarseness))
                        topLeft = Pair(long, lat.add(zone.tyleCoarseness))
                        bottomLeft = Pair(long, lat)
                        // Skip the Tyle if a hole is specified at this coordinate
                        if (!zone.holes.any { it.first == long && it.second == lat })
                            bufferedWriter.append("[[${bottomRight.first.stripTrailingZeros().toPlainString()}," +
                                                    "${bottomRight.second.stripTrailingZeros().toPlainString()}]," +
                                                    "[${topRight.first.stripTrailingZeros().toPlainString()}," +
                                                    "${topRight.second.stripTrailingZeros().toPlainString()}]," +
                                                    "[${topLeft.first.stripTrailingZeros().toPlainString()}," +
                                                    "${topLeft.second.stripTrailingZeros().toPlainString()}]," +
                                                    "[${bottomLeft.first.stripTrailingZeros().toPlainString()}," +
                                                    "${bottomLeft.second.stripTrailingZeros().toPlainString()}]," +
                                                    "[${bottomRight.first.stripTrailingZeros().toPlainString()}," +
                                                    "${bottomRight.second.stripTrailingZeros().toPlainString()}]]")


                        bufferedWriter.append("]},\"properties\":{")
                        // Insert any specified properties into each Tyle
                        if (zone.properties.isNotEmpty()) insertProperties(zone.properties)
                        bufferedWriter.append("}}")
                        long += zone.tyleCoarseness
                }
                lat += zone.tyleCoarseness
            }
            bufferedWriter.append("]}")
            bufferedWriter.flush()
        }
        catch (io: IOException) {
            throw FogMachineException("ERROR: An error occurred while generating GeoJSON file.", io)
        }
    }

    /**
     * Perform GeoJSON generation on the given [myst]. Any Zone holes specified will be ''punched'' out
     * of the MultiPolygon.
     * @param myst The built [MystBuilder] object containing all the necessary parameters to create a GeoJSON file.
     * @throws FogMachineException If error occurs while generating GeoJSON.
     */
    private fun generateMystFog(myst: FogType.Myst) {
        try {
            // Initial
            bufferedWriter.append("{\"type\":\"FeatureCollection\",\"features\":[{")

            // Inverse Mask to cover Earth
            bufferedWriter.append("\"type\":\"Feature\",\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":[[")
            bufferedWriter.append("["
                                + "[" + 0 + "," + 90 + "],"
                                + "[" + -180 + "," + 90 + "],"
                                + "[" + -180 + "," + 0 + "],"
                                + "[" + -180 + "," + -90 + "],"
                                + "[" + 0 + "," + -90 + "],"
                                + "[" + 180 + "," + -90 + "],"
                                + "[" + 180 + "," + 90 + "],"
                                + "[" + 0 + "," + 90 + "]"
                                + "]")

            // Holes that will 'punch out' Zones in the MultiPolygon
            for (hole in myst.holes) {
                bottomRight = Pair (hole.first.add(myst.zoneCoarseness), hole.second)
                topRight = Pair (hole.first.add(myst.zoneCoarseness), hole.second.add(myst.zoneCoarseness))
                topLeft = Pair (hole.first, hole.second.add(myst.zoneCoarseness))
                bottomLeft = Pair (hole.first, hole.second)

                bufferedWriter.append(",["
                                    + "[" + bottomRight.first.stripTrailingZeros().toPlainString() + ","
                                    + bottomRight.second.stripTrailingZeros().toPlainString() + "],"

                                    + "[" + bottomLeft.first.stripTrailingZeros().toPlainString() + ","
                                    + bottomLeft.second.stripTrailingZeros().toPlainString() + "],"

                                    + "[" + topLeft.first.stripTrailingZeros().toPlainString() + ","
                                    + topLeft.second.stripTrailingZeros().toPlainString() + "],"

                                    + "[" + topRight.first.stripTrailingZeros().toPlainString() + ","
                                    + topRight.second.stripTrailingZeros().toPlainString() + "],"

                                    + "[" + bottomRight.first.stripTrailingZeros().toPlainString() + ","
                                    + bottomRight.second.stripTrailingZeros().toPlainString() + "]"
                                    + "]")
            }
            bufferedWriter.append("]]},\"properties\":{")
            // Insert any specified properties into the MultiPolygon
            if (myst.properties.isNotEmpty()) insertProperties(myst.properties)
            bufferedWriter.append("}}]}")
        }
        catch (io: IOException) {
            throw(FogMachineException("ERROR: An error occurred while generating GeoJSON file.", io))
        }
    }

    /**
     * Adds the given [properties] into a GeoJSON Feature.
     * @param properties The [MutableList] of properties to be inserted.
     */
    private fun insertProperties(properties: MutableList<Property>) {
        var first = true
        for (property in properties) {
            if (first) first = false else bufferedWriter.append(",")
            bufferedWriter.append("\""  + property.name
                                + "\":" + property.value)
        }
    }
}

@DslMarker
private annotation class FogDSL

/**
 * A receiver function to generate a Zone GeoJSON file. The file will be created upon substantiation.
 *
 * ### Required Parameters:
 *
 * - `path`: The location of the GeoJSON file to be generated. Suffixing with '.geojson' is optional.
 * - `latitude`: The starting latitude for generation, beginning at the west-most (left) position.
 * - `longitude`: The starting longitude for generation, beginning at the south-most (down) position.
 * - `tileCoarseness`: The ''Coarseness'' of each Tyle, i.e. the width and height of each Tyle, in Decimal Degrees.
 * - `zoneCoarseness`: The ''Coarseness'' of the Zone, i.e. the width and height of the Zone, in Decimal Degrees.
 *
 * ### Optional Parameters:
 *
 * - `properties`: A receiver function to build and insert properties into each generated Tyle.
 *   See [PropertiesBuilder] for more details.
 * - `holes`: A receiver function to build and insert Tyle holes into the generated Zone. See [Hole] for more details.
 *
 *
 * See [Coarseness] for details on supported `tileCoarseness` and `zoneCoarseness` values.
 */
fun zone(block: ZoneBuilder.() -> Unit): Unit = ZoneBuilder().apply(block).build()


/**
 * A receiver to generate a Myst GeoJSON file. The file will be created upon substantiation.
 *
 * ### Required Parameters:
 *
 * - `path`: The location of the GeoJSON file to be generated. Suffixing with '.geojson' is optional.
 *
 *
 * ### Optional Parameters:
 *
 * - `zoneCoarseness`: The ''Coarseness'' of the Zone holes, i.e. the width and height of the Zone
 *   hole(s), in Decimal Degrees. Required when specifying `holes`.
 * - `properties`: A receiver function to build and insert properties into each generated Tyle.
 *   See [Property] for more details.
 * - `holes`: A receiver function to build and insert Zone holes into the generated Myst.
 *   `zoneCoarseness` must be also be specified. See [Hole] for more details.
 *
 *
 * See [Coarseness] for details on supported `tileCoarseness` and `zoneCoarseness` values.
 */
fun myst(block: MystBuilder.() -> Unit): Unit = MystBuilder().apply(block).build()

/**
 * A Builder Class to generate a Zone GeoJSON.
 */
@FogDSL class ZoneBuilder : FogBuilder() {

    /**=============================================================================================================***/
    /**
     * The Longitude of the Zone, which is at the bottom left starting point. Any [BigDecimal] supported datatype
     * is also supported.
     * @throws FogMachineException If Latitude was already set or is being set with an unsupported type.
     */
    var longitude: Any                                                                                             /***/
        get() = mutableLongitude                                                                                   /***/
        set(value) {                                                                                               /***/
            try {                                                                                                  /***/
                mutableLongitude = BigDecimal(value.toString())                                                    /***/
                    .takeIf { !lngSet }                                                                            /***/
                    .also { lngSet = true }                                                                        /***/
                    ?: throw FogMachineException("Error: Longitude should not be set more than once!")             /***/
            }                                                                                                      /***/
            catch (nfe : NumberFormatException) {                                                                  /***/
                throw FogMachineException("Error: Longitude not a valid value: $value", nfe)                       /***/
            }                                                                                                      /***/
        }                                                                                                          /***/
    private var lngSet = false                                                                                     /***/
    private var mutableLongitude : BigDecimal = BigDecimal.ZERO                                                    /***/
    /**==============================================================================================================**/
    /**=============================================================================================================***/
    /**
     * The Latitude of the Zone, which is at the bottom left starting point. Any [BigDecimal] supported datatype
     * is also supported.
     *
     * @throws FogMachineException If Latitude was already set or is being set with an unsupported type.
     */
    var latitude: Any                                                                                              /***/
    get() = mutableLatitude                                                                                        /***/
    set(value) {                                                                                                   /***/
        try {                                                                                                      /***/
            mutableLatitude = BigDecimal(value.toString())                                                         /***/
                .takeIf { !latSet }                                                                                /***/
                .also { latSet = true }                                                                            /***/
                ?: throw FogMachineException("Error: Latitude should not be set more than once!")                  /***/
        } catch (nfe: NumberFormatException) {                                                                     /***/
            throw FogMachineException("Error: Latitude not a valid value: $value", nfe)                            /***/
        }                                                                                                          /***/
    }                                                                                                              /***/
    private var latSet = false                                                                                     /***/
    private var mutableLatitude : BigDecimal = BigDecimal.ZERO                                                     /***/
    /**=============================================================================================================***/
    /**==============================================================================================================**/
    /**
     * The Tyle Coarseness of the Zone, which defines the length and width of each Polygon Feature. See [Coarseness]
     * for available values.
     *
     * @throws FogMachineException If Tyle Coarseness was already set or is being set with an unsupported value.
     */
    var tyleCoarseness: Coarseness                                                                                 /***/
        get() = Coarseness.values().find { BigDecimal.ONE.movePointLeft(it.ordinal) == mutableTyleCoarseness }     /***/
                ?: throw FogMachineException("Error: Invalid Tyle coarseness value: $mutableTyleCoarseness")       /***/
        set(value) {                                                                                               /***/
            mutableTyleCoarseness = BigDecimal.ONE.movePointLeft(value.ordinal)                                    /***/
                .takeIf { !tCSet }                                                                                 /***/
                .also { tCSet = true }                                                                             /***/
                ?: throw FogMachineException("Error: Tyle coarseness should not be set more than once!")           /***/
        }                                                                                                          /***/
    private var tCSet = false                                                                                      /***/
    private var mutableTyleCoarseness : BigDecimal = BigDecimal.ZERO                                               /***/
    /**==============================================================================================================**/

    /**
     * Helper code to allow infix functions as an alternative to set longitude and/or latitude.
     *
     */
    inner class CoordsStart {
        /**
         * Infix function to define longitude.
         */
        infix fun lng(lng: Any) = CoordLng(lng)
        /**
         * Infix function to define latitude.
         */
        infix fun lat(lat: Any) = CoordLat(lat)
        /**
         * Nested Class to handle Zone Coarseness if Tyle Coarseness was specified first.
         *
         * (Ex. `coord lng 31.134 lat 29.978`)
         */
        inner class CoordLng(private val lng: Any) {
            /**
             * Nested infix function to define latitude.
             *
             * @throws FogMachineException If latitude was already set or is being set with an unsupported type.
             */
            infix fun lat(lat: Any) {
                try {
                    mutableLatitude = BigDecimal(lat.toString())
                        .takeIf { !latSet }
                        .also { latSet = true }
                        ?: throw FogMachineException("Error: Latitude should not be set more than once!")
                } catch (nfe: NumberFormatException) {
                    throw FogMachineException("Error: Latitude not a valid value: $lat", nfe)
                }
            }
        }
        inner class CoordLat(private val lat: Any) {
            /**
             * Nested infix function to define longitude.
             *
             * @throws FogMachineException If longitude was already set or is being set with an unsupported type.
             */
            infix fun lng(lng: Any) {
                try {
                    mutableLongitude = BigDecimal(lng.toString())
                        .takeIf { !lngSet }
                        .also { lngSet = true }
                        ?: throw FogMachineException("Error: Longitude should not be set more than once!")
                } catch (nfe: NumberFormatException) {
                    throw FogMachineException("Error: Longitude not a valid value: $lng", nfe)
                }
            }
        }
    }
    /**
     * The entry point for infix logic. Specify `coord` in a Zone DSL to use this.
     */
    val coord: CoordsStart
        get() = CoordsStart()

    /**
     * Helper code to allow infix functions as an alternative to set Tyle and/or Zone Coarseness.
     */
    inner class CoarsenessStart {
        /**
         * Infix function to define Tyle Coarseness.
         */
        infix fun tyle(tC: Coarseness) = CoarseTyle(tC)
        /**
         * Infix function to define Zone Coarseness.
         */
        infix fun zone(zC: Coarseness) = CoarseZone(zC)
        /**
         * Nested Class to handle Zone Coarseness if Tyle Coarseness was specified first.
         *
         * (Ex. `coarseness tyle MEDIUM zone COARSE`)
         */
        inner class CoarseTyle(private val tyle: Any) {
            /**
             * Nested infix function to define Zone Coarseness.
             */
            infix fun zone(zone: Coarseness) {
                    mutableTyleCoarseness = if (!tCSet) BigDecimal.ONE.movePointLeft(zone.ordinal)
                        .also { tCSet = true }
                        else throw FogMachineException("Error: Tyle coarseness should not be set more than once!")
            }
        }
        /**
         * Nested Class to handle Tyle Coarseness if Zone Coarseness was specified first.
         *
         * (Ex. `coarseness zone COARSE tyle MEDIUM`)
         */
        inner class CoarseZone(private val zone: Any) {
            /**
             * Nested infix function to define Tyle Coarseness.
             */
            infix fun tyle(tyle: Coarseness) {
                    mutableZoneCoarseness =  if (!zCSet) BigDecimal.ONE.movePointLeft(tyle.ordinal)
                        .also { zCSet = true }
                        else throw FogMachineException("Error: Zone coarseness should not be set more than once!")
            }
        }
    }
    /**
     * The entry point for infix logic. Specify `coarseness` in a Zone DSL to use this.
     */
    val coarseness: CoarsenessStart
        get() = CoarsenessStart()

    /**
     * Build this [ZoneBuilder] object, sending the substantiated [FogType.Zone] to [FogMachine].
     * @throws FogMachineException If [ZoneBuilder] object has invalid parameters, such as mismatched Tyle / Zone
     * Coarseness scales, duplicate holes, or out of bounds holes.
     */
    internal fun build() {
        if (mutableTyleCoarseness > mutableZoneCoarseness)
            throw FogMachineException("Error: Tyle Coarseness cannot be larger than Zone Coarseness!")
        if(mutableLongitude.scale() > mutableTyleCoarseness.scale())
            throw FogMachineException("Error: Longitude scale ${mutableLongitude.scale()} cannot be larger " +
                    "than Tyle Coarseness scale ${mutableTyleCoarseness.scale()} (${tyleCoarseness.name})!")
        if(mutableLatitude.scale() > mutableTyleCoarseness.scale())
            throw FogMachineException("Error: Latitude scale ${mutableLatitude.scale()} cannot be larger " +
                    "than Tyle Coarseness scale ${mutableTyleCoarseness.scale()} (${tyleCoarseness.name})!")
        if (tyleCoarseness == Coarseness.FINE
            && (zoneCoarseness == Coarseness.SUPER_COARSE
                    || zoneCoarseness == Coarseness.SUPER_DUPER_COARSE))
            println("Warning: With these Coarseness settings, the generated GeoJSON file may be extremely large.")

        // Handle holes only after build() is called; this ensures mutableTyleCoarseness
        // and mutableZoneCoarseness have been defined
        // Send specified hole data to corresponding handler
        mutableHoles.forEach {
            when (it.coordinates) {
                null -> {}
                is String -> handleStringHoleCoordinates(it.coordinates)
                is Pair<*, *> -> handlePairHoleCoordinates(it.coordinates)
                is List<*> -> handleListHoleCoordinates(it.coordinates)
                else -> throw FogMachineException("Error: Invalid coordinates format: ${it.coordinates}")
            }
            when (it.ids) {
                null -> {}
                is String -> handleStringHoleIDs(it.ids)
                is List<*> -> handleListHoleIDs(it.ids)
                is Int -> handleNumberHoleIDs(it.ids)
                else -> throw FogMachineException("Error: Invalid IDs format: ${it.ids}")
            }
        }

        // Check for duplicate holes
        if (mutableLngLatHoles.distinct().count() != mutableLngLatHoles.count()) {
            throw FogMachineException("Error: Duplicate holes are not allowed!")
        }

        // Check for holes outside geographic area
        mutableLngLatHoles.forEach {
            if (it.first < mutableLongitude || it.first > mutableLongitude.add(mutableZoneCoarseness)
                || it.second < mutableLatitude || it.second > mutableLatitude.add(mutableZoneCoarseness)) {
                throw FogMachineException("Error: hole $it was out of bounds of generated area!")
            }
        }

        // Send Zone to FogMachine
        FogMachine(
            FogType.Zone(mutablePath, mutableLatitude, mutableLongitude,
                mutableTyleCoarseness, mutableZoneCoarseness, mutableProperties, mutableLngLatHoles)
        )
    }
    /**
     * Parse and consume a [String] that describes a particular Tyle Polygons' ID.
     * @param hole The String that describes the ID of the Tyle to be removed.
     * @throws FogMachineException If [hole] is in an invalid format, or if parsed ID is out of bounds.
     */
    override fun handleStringHoleIDs(hole: String) {
        val id : BigDecimal
        try {
            id = BigDecimal(hole)
        }
        catch (nfe : NumberFormatException) {
            throw FogMachineException("Error: invalid ID format! $hole")
        }
        val numLength = mutableZoneCoarseness.divide(mutableTyleCoarseness)
        val xVal = id.remainder(numLength)
        val yVal = id.divide(numLength, RoundingMode.DOWN)

        val maxID = numLength.pow(2).minus(BigDecimal.ONE)
        if(id < BigDecimal.ZERO || id > maxID) {
            throw FogMachineException("Error: ID out of bounds! Range: [0, $maxID]  ID: $hole")
        }

        mutableLngLatHoles.add(
            Pair(
                mutableLongitude.add(xVal.multiply(mutableTyleCoarseness)).stripTrailingZeros(),
                mutableLatitude.add(yVal.multiply(mutableTyleCoarseness)).stripTrailingZeros()
            )
        )
    }
    /**
     * Parse and consume an [Int] that describes a particular Tyle Polygons' ID.
     * @param hole The integer that describes the ID of the Tyle to be removed.
     * @throws FogMachineException If parsed ID is out of bounds.
     */
    override fun handleNumberHoleIDs(hole : Int) {
        val id = BigDecimal(hole.toString())
        val numLength = mutableZoneCoarseness.divide(mutableTyleCoarseness)
        val xVal = id.remainder(numLength)
        val yVal = BigDecimal(id.toInt() / numLength.toInt())

        val maxID = numLength.pow(2).minus(BigDecimal.ONE)
        if(hole < 0 || hole > numLength.pow(2).toInt() - 1) {
            throw FogMachineException("Error: ID out of bounds! Range: [0, $maxID]  ID: $hole")
        }

        mutableLngLatHoles.add(
            Pair(
                mutableLongitude.add(xVal.multiply(mutableTyleCoarseness)).stripTrailingZeros(),
                mutableLatitude.add(yVal.multiply(mutableTyleCoarseness)).stripTrailingZeros()
            )
        )
    }
    /**
     * Parse and consume a [List] that describes  one or more Tyle Polygons' ID. The list must consist only of
     * [String] and/or [Int] types.
     * @param holes The collection that describes the ID(s) of the Tyle(s) to be removed.
     * @throws FogMachineException If List contains types other than String and Int.
     */
    override fun handleListHoleIDs(holes : List<*>) {
        for (hole in holes) {
            when (hole) {
                is String -> handleStringHoleIDs(hole)
                is Int -> handleNumberHoleIDs(hole)
                else -> throw FogMachineException("Error: Unsupported item(s) in list! $holes")
            }
        }
    }
    /**
     * Parse and consume a [Pair] containing Longitude/Latitude coordinates that define a [hole] in the Zone.
     * @param hole The Pair that describes the coordinates of the Tyle to be removed.
     * @throws FogMachineException If [hole] contains types other than [BigDecimal], [String], or [Number].
     */
    override fun handlePairHoleCoordinates(hole : Pair<*, *>) {
        val first : Any
        val second : Any
        first = when (hole.first) {
            is BigDecimal -> (hole.first as BigDecimal)
            is String, is Number -> BigDecimal(hole.first.toString())
            else -> throw FogMachineException("Error: Unsupported Pair Object!")
        }
        second = when (hole.second) {
            is BigDecimal -> (hole.second as BigDecimal)
            is String, is Number -> BigDecimal(hole.second.toString())
            else -> throw FogMachineException("Error: Unsupported Pair Object!")
        }

        if (first.scale() > mutableTyleCoarseness.scale() || second.scale() > mutableTyleCoarseness.scale())
            throw FogMachineException("Error: Scale of coordinates ($first, $second) " +
                    "must match Tyle Coarseness $mutableTyleCoarseness scale!")

        mutableLngLatHoles.add(
            Pair(
                first.setScale(mutableTyleCoarseness.scale()).stripTrailingZeros(),
                second.setScale(mutableTyleCoarseness.scale()).stripTrailingZeros()
            )
        )
    }
    /**
     * Parse and consume a [List] that describes a one or more Tyle Polygons' coordinates. The list must consist only
     * of [Pair] objects.
     * @param holes The collection that describes the coordinates of the Tyle(s) to be removed.
     * @throws FogMachineException If [holes] contains types other than Pairs.
     */
    override fun handleListHoleCoordinates(holes : List<*>) {
        val filteredHoles = holes.filterIsInstance<Pair<BigDecimal, BigDecimal>>()
        if(filteredHoles.count() < holes.count()) {
            throw FogMachineException("Error: Unsupported item(s) in list! $holes")
        }
        for (hole in filteredHoles) {
            handlePairHoleCoordinates(hole)
        }
    }
}

/**
 * A Builder Class to generate a Myst GeoJSON.
 */
@FogDSL class MystBuilder : FogBuilder() {
    /**
     * Build this [MystBuilder] object, sending the substantiated Myst to [FogMachine].
     *
     * @throws FogMachineException If [MystBuilder] object has invalid parameters, such as mismatched
     * Longitude/Latitude & Zone Coarseness scales, duplicate holes, or out of bounds holes.
     */
    internal fun build() {
        // Handle holes only after build() is called; this ensures mutableZoneCoarseness has been defined
        for (hole in mutableHoles) {
            when (hole.coordinates) {
                null -> {}
                is String -> handleStringHoleCoordinates(hole.coordinates)
                is Pair<*, *> -> handlePairHoleCoordinates(hole.coordinates)
                is List<*> -> handleListHoleCoordinates(hole.coordinates)
                else -> throw FogMachineException("Error: Invalid coordinates format: ${hole.coordinates}")
            }
            when (hole.ids) {
                null -> {}
                is String -> handleStringHoleIDs(hole.ids)
                is List<*> -> handleListHoleIDs(hole.ids)
                is Int -> handleNumberHoleIDs(hole.ids)
                else -> throw FogMachineException("Error: Invalid IDs format: ${hole.ids}")
            }
        }

        mutableLngLatHoles.forEach {
            if(it.first.scale() > mutableZoneCoarseness.scale())
                throw FogMachineException("Error: Longitude scale ${it.first.scale()} cannot be larger " +
                        "than Zone Coarseness scale ${mutableZoneCoarseness.scale()} (${zoneCoarseness.name})!")
            if(it.second.scale() > mutableZoneCoarseness.scale())
                throw FogMachineException("Error: Latitude scale ${it.second.scale()} cannot be larger " +
                        "than Zone Coarseness scale ${mutableZoneCoarseness.scale()} (${zoneCoarseness.name})!")
        }

        if (mutableLngLatHoles.isEmpty() && Coarseness.values()
            .find { BigDecimal.ONE.movePointLeft(it.ordinal) == mutableZoneCoarseness } != null)
                throw FogMachineException("Zone Coarseness should only be defined if holes are specified!")

        FogMachine(FogType.Myst(mutablePath, mutableZoneCoarseness, mutableProperties, mutableLngLatHoles))
    }
    /**
     * Parse and consume a [String] that describes a particular Zones' ID (file name).
     * @param hole The hole that describes the ID of the Zone to be removed.
     * @throws FogMachineException If [hole] is in an invalid format.
     */
    override fun handleStringHoleIDs(hole: String) {
        try {
            mutableLngLatHoles.add(reverseCantor(hole.toInt()))
        }
        catch (nfe: NumberFormatException) {
            throw FogMachineException("Error: invalid ID format! $hole")
        }
    }
    /**
     * Parse and consume a [Int] that describes a particular Zones' ID (file name).
     * @param hole The hole that describes the ID of the Zone to be removed.
     */
    override fun handleNumberHoleIDs(hole : Int) {
            mutableLngLatHoles.add(reverseCantor(hole))
    }
    /**
     * Parse and consume a [List] that describes one or more Zones' ID (file name). The list must consist only of
     * [String] and/or [Int] types.
     * @param holes The collection that describes the ID(s) of the Zone(s) to be removed.
     * @throws FogMachineException If List contains types other than String and Int.
     */
    override fun handleListHoleIDs(holes : List<*>) {
        for (hole in holes) {
            when (hole) {
                is String -> handleStringHoleIDs(hole)
                is Int -> handleNumberHoleIDs(hole)
                else -> throw FogMachineException("Error: Unsupported item(s) in list! $holes")
            }
        }
    }
    /**
     * Parse and consume a [Pair] containing Longitude/Latitude coordinates that define a Zone.
     * @param hole The hole that describes the coordinates of the Zone to be removed.
     * @throws FogMachineException If [hole] contains types other than [BigDecimal], [String], or [Number].
     */
    override fun handlePairHoleCoordinates(hole : Pair<*, *>) {
        val first : Any
        val second : Any
        first = when (hole.first) {
            is BigDecimal -> (hole.first as BigDecimal)
            is String, is Number -> BigDecimal(hole.first.toString())
            else -> throw FogMachineException("Error: Unsupported Pair Object!")
        }
        second = when (hole.second) {
            is BigDecimal -> (hole.second as BigDecimal)
            is String, is Number -> BigDecimal(hole.second.toString())
            else -> throw FogMachineException("Error: Unsupported Pair Object!")
        }

        if (first.scale() > mutableZoneCoarseness.scale() || second.scale() > mutableZoneCoarseness.scale())
            throw FogMachineException("Error: Scale of coordinates ($first, $second)" +
                    " must match Zone Coarseness $mutableZoneCoarseness scale!")

        mutableLngLatHoles.add(
            Pair(
                first.setScale(mutableZoneCoarseness.scale()).stripTrailingZeros(),
                second.setScale(mutableZoneCoarseness.scale()).stripTrailingZeros()
            )
        )
    }
    /**
     * Parse and consume a [List] that describes a one or more Zone coordinates. The list must consist only
     * of [Pair] objects.
     * @param holes The collection that describes the coordinates of the Zone(s) to be removed.
     * @throws FogMachineException If [holes] contains types other than Pairs.
     */
    override fun handleListHoleCoordinates(holes : List<*>) {
        val filteredHoles = holes.filterIsInstance<Pair<BigDecimal, BigDecimal>>()
        if(filteredHoles.count() < holes.count()) {
            throw FogMachineException("Error: Unsupported item(s) in list! $holes")
        }
        for (hole in filteredHoles) {
            handlePairHoleCoordinates(hole)
        }
    }

    /**
     * An implementation of the Inverse of the Cantor Pairing Function, π(x, y).
     *
     * This function returns a [Pair] of [BigDecimal] natural numbers such that [value] = π(x, y). [value] must be
     * a natural number (i.e an integer > 0).
     * @param value The value to be used in the Inverse Cantor Pairing Function.
     * @throws FogMachineException if [value] is not a natural number.
     */
    private fun reverseCantor(value: Int) : Pair<BigDecimal, BigDecimal> {
        if (value < 1) throw FogMachineException("Inverse Cantor Paring Function may only use natural numbers! $value")
        val lng: BigDecimal
        val lat: BigDecimal
        val multiplicand = BigDecimal("0.1")
        val t: Int = floor((-1 + sqrt((1 + 8 * value).toDouble())) / 2).toInt()
        lng = BigDecimal(t * (t + 3) / 2 - value)
            .multiply(multiplicand)
            .minus(BigDecimal(180))
        lat = BigDecimal(value - t * (t + 1) / 2)
            .multiply(multiplicand)
            .minus(BigDecimal(90))
        return Pair(lng, lat)
    }
}

/**
 * Helper class to allow an arbitrary number of properties to be added to [ZoneBuilder] or [MystBuilder] DSLs.
 */
@FogDSL class PROPERTIES: ArrayList<Property>() {
    /**
     * Receiver function to define one property (key-value pair) to add to the GeoJSON file.
     *
     * Options:
     * - name: The name of property.
     * - value: The value of the property. Supported types include all the standard JSON datatypes:
     *  [String], [Number], [Boolean], [JSONArray], [JSONObject], and `null`.
     */
    internal fun property(block: PropertiesBuilder.() -> Unit) {
        add(PropertiesBuilder().apply(block).build())
    }

    /**
     * Infix function to override [String.value] as an alternative to set properties.
     * @param propertyValue the value of the property being set.
     */
    infix fun String.value(propertyValue: Any?) {
        add(PropertiesBuilder().apply {
            mutableName = this@value.lowercase(Locale.getDefault())
                .takeIf { !nameSet }
                .also { nameSet = true }
                ?: throw FogMachineException("Error: Property name should not be set more than once! ${this@value}")
            value = propertyValue
        }.build())
    }
}

/**
 * A Builder Class to handle GeoJSON properties to be added to the generated file.
 */
@FogDSL internal class PropertiesBuilder {
    /**=============================================================================================================***/
    /**
     * The name of the GeoJSON property.
     * @throws FogMachineException If the property name was already set.
     */
    var name: String                                                                                               /***/
        get() = mutableName                                                                                        /***/
        set(value) {                                                                                               /***/
            mutableName = value.lowercase(Locale.getDefault())                                                     /***/
                .takeIf{ !nameSet }                                                                                /***/
                .also { nameSet = true }                                                                           /***/
                ?: throw FogMachineException("Error: Property name should not be set more than once!")             /***/
        }                                                                                                          /***/
    var mutableName: String = ""                                                                                   /***/
    var nameSet = false                                                                                            /***/
    /**==============================================================================================================**/
    /**==============================================================================================================**/
    /**
     * The value of the GeoJSON property.
     * @throws FogMachineException If the property value was already set.
     */
    var value: Any?                                                                                                /***/
        get() = mutableValue                                                                                       /***/
        set(value) {                                                                                               /***/
        if (valueSet)                                                                                              /***/
            throw FogMachineException("Error: Property \"$name\" value should not be set more than once! $value")  /***/
        mutableValue = if (value is String) "\"" + value +  "\"" else value                                        /***/
        valueSet = true                                                                                            /***/
    }                                                                                                              /***/
    private var mutableValue : Any? = null                                                                         /***/
    private var valueSet = false                                                                                   /***/
    /**==============================================================================================================**/

    /**
     * Build this [PropertiesBuilder] object, creating a [Property].
     * @return The built Property.
     */
    fun build() : Property {
        nameSet = false
        valueSet = false
        println("Successfully built: $name: $value")
        return Property(mutableName, mutableValue)
    }
}

/**
 * A data type that defines a GeoJSON Property, a key-value pair.
 * @param name The name of the property.
 * @param value the value of the property.
 * Supported types: [String], [Number], [Boolean], [JSONObject], [JSONArray], or `null`.
 */
@FogDSL data class Property(val name: String, val value: Any?)

/**
 * A data type that defines a hole in the [FogType.Zone] or [FogType.Myst].
 * @param coordinates The Longitude/Latitude coordinates (bottom-left most) of the Tyle or Zone to be removed.
 * Supported types: [String], [Pair]<[BigDecimal], [BigDecimal]>, [List]<[Pair]<[BigDecimal], [BigDecimal]>>.
 * @param ids The ID of the Tyle or Zone to be removed.
 * Supported types: [String], [Int], [List]<[String] and/or [Int]>
 */
@FogDSL data class Hole(val coordinates: Any?, val ids: Any?)

/**
 * A super Class that defines the two types of supported GeoJSON types: [FogType.Zone] and [FogType.Myst].
 */
@FogDSL class FogType {

    /**
     * A data type that defines a Zone for use in the [FogMachine].
     */
    data class Zone(val path: String, val latitude : BigDecimal, val longitude: BigDecimal,
                    val tyleCoarseness: BigDecimal, val zoneCoarseness: BigDecimal,
                        val properties: MutableList<Property>, val holes: MutableList<Pair<BigDecimal, BigDecimal>>) {
        /**
         * A subclass that defines a [HoleBuilder] variant specifically for [ZoneBuilder].
         */
        @FogDSL class ZoneHoleBuilder : HoleBuilder() {
            /**
             * Build the [Hole] to be included in this [Zone].
             * @return The built hole.
             */
            fun build() : Hole = Hole(mutableCoordinates, mutableIds)
        }
    }

    /**
     * A data type that defines a Myst for use in the [FogMachine].
     */
    data class Myst(val path: String, val zoneCoarseness: BigDecimal,
                    val properties: MutableList<Property>, val holes: MutableList<Pair<BigDecimal, BigDecimal>>) {
        /**
         * A subclass that defines a [HoleBuilder] variant specifically for [MystBuilder].
         */
        @FogDSL class MystHoleBuilder : HoleBuilder() {
            /**
             * Build the [Hole] to be included in this [Myst].
             * @return The built hole.
             */
            fun build() : Hole = Hole(mutableCoordinates, mutableIds)
        }
    }
}

/**
 * A Builder Class to handle any holes defined to be included in the generated GeoJSON file.
 */
@FogDSL open class HoleBuilder {
    /**==============================================================================================================**/
    /**
     * The coordinates (Longitude/Latitude) of the hole.
     * @throws FogMachineException If hole coordinates was already set.
     */
    var coordinates: Any?                                                                                          /***/
        get() = mutableCoordinates                                                                                 /***/
        set(value) {                                                                                               /***/
            mutableCoordinates = value                                                                             /***/
                .takeIf { !coordsSet }                                                                             /***/
                .also { coordsSet = true }                                                                         /***/
                ?: throw FogMachineException("Error: Hole Coordinates should not be set more than once!")          /***/
        }                                                                                                          /***/
    private var coordsSet = false                                                                                  /***/
    protected var mutableCoordinates: Any? = null                                                                  /***/
    /**==============================================================================================================**/
    /**==============================================================================================================**/
    /**
     * The ID of the hole. This is the Tyle ID if used in `zone = {...}` , or Zone ID if used in `myst = {...}`.
     * @throws FogMachineException If hole ID was already set.
     */
    var ids: Any?                                                                                                  /***/
        get() = mutableIds                                                                                         /***/
        set(value) {                                                                                               /***/
            mutableIds = value                                                                                     /***/
                .takeIf { !idsSet }                                                                                /***/
                .also { idsSet = true }                                                                            /***/
                ?: throw FogMachineException("Error: Hole IDs should not be set more than once!")                  /***/
        }                                                                                                          /***/
    private var idsSet = false                                                                                     /***/
    protected var mutableIds: Any? = null                                                                          /***/
    /**==============================================================================================================**/
}

/**
 * A generic Builder class that houses the shared parameters used in [ZoneBuilder] and [MystBuilder].
 */
@FogDSL abstract class FogBuilder {
    /**
     * Shorter definitions of each [Coarseness] level for more readability in infix functions.
     */
    val FINE: Coarseness = Coarseness.FINE
    val MEDIUM: Coarseness = Coarseness.MEDIUM
    val COARSE: Coarseness = Coarseness.COARSE
    val SUPER_COARSE: Coarseness = Coarseness.SUPER_COARSE
    val SUPER_DUPER_COARSE: Coarseness = Coarseness.SUPER_DUPER_COARSE

    /**==============================================================================================================**/
    /**
     * The path of the GeoJSON file to be generated.
     * @throws FogMachineException If path was already set.
     */
    var path: String                                                                                               /***/
        get() = mutablePath                                                                                        /***/
        set(value) {                                                                                               /***/
            mutablePath = (value.takeIf { it.endsWith(".geojson") } ?: "$value.geojson")                     /***/
                .takeIf { !pathSet }                                                                               /***/
                .also { pathSet = true }                                                                           /***/
                ?: throw FogMachineException("Error: Path should not be set more than once!")                      /***/
        }                                                                                                          /***/
    private var pathSet = false                                                                                    /***/
    protected var mutablePath : String = ""                                                                        /***/
    /**==============================================================================================================**/
    /**==============================================================================================================**/
    /**
     * The Zone Coarseness of the file, which defines the length and width of the generated area in `zone = {...}`,
     * or the length and width of any holes specified in `myst = {...}`. See [Coarseness]
     * for available values.
     * @throws FogMachineException If Zone Coarseness was already set or is not set when holes are specified.
     */
    var zoneCoarseness: Coarseness                                                                                 /***/
        get() = Coarseness.values().find { BigDecimal.ONE.movePointLeft(it.ordinal) == mutableZoneCoarseness }     /***/
            ?: throw FogMachineException("Error: Zone Coarseness is required when holes are specified!")           /***/
        set(value) {                                                                                               /***/
            mutableZoneCoarseness = BigDecimal.ONE.movePointLeft(value.ordinal)                                    /***/
                .takeIf { !zCSet }                                                                                 /***/
                .also { zCSet = true }                                                                             /***/
                ?: throw FogMachineException("Error: Zone Coarseness should not be set more than once!")           /***/
        }                                                                                                          /***/
    protected var zCSet = false                                                                                    /***/
    protected var mutableZoneCoarseness : BigDecimal = BigDecimal.ZERO                                             /***/
    /**==============================================================================================================**/
    /**==============================================================================================================**/
    /**
     * Receiver function to define a property that will be added to all Tyles in a Zone or the Zones in a Myst.
     * There are two options to define properties:
     *
     * - Using the infix function (Ex. `"prop1" value "0"`)
     * - Using the receiver function: `property{...}` (See [Property] for details on usage)
     *
     * Any arbitrary number of these two options are allowed.
     *
     * Supported property value types: [String], [Number], [Boolean], [JSONObject], [JSONArray], or `null`.
     * @throws FogMachineException If property value is an unsupported type.
     */
    fun properties(block: PROPERTIES.() -> Unit) {                                                                 /***/
        mutableProperties.addAll(PROPERTIES().apply(block))                                                        /***/
        mutableProperties.forEach { when(it.value) {                                                      /***/
            is String, is Number, is Boolean, is JSONObject, is JSONArray, null -> return@forEach                  /***/
            else -> throw FogMachineException("Error: Property: ${it.name} has an unsupported value: ${it.value}") /***/
            }                                                                                                      /***/
        }                                                                                                          /***/
    }                                                                                                              /***/
    protected val mutableProperties = mutableListOf<Property>()                                                    /***/
    /**==============================================================================================================**/

    protected var mutableHoles = mutableListOf<Hole>()

    protected var mutableLngLatHoles = mutableListOf<Pair<BigDecimal, BigDecimal>>()

    abstract fun handleStringHoleIDs(hole: String)

    abstract fun handleNumberHoleIDs(hole : Int)

    abstract fun handleListHoleIDs(holes : List<*>)

    abstract fun handlePairHoleCoordinates(hole : Pair<*, *>)

    abstract fun handleListHoleCoordinates(holes : List<*>)

    /**
     * Receiver function to route and handle hole creation if the current [FogBuilder] is a [ZoneBuilder].
     *
     * Options:
     *  - `ids`: The Tyle ID(s) of the to be removed.
     *  - `coordinates`: The Longitude/Latitude coordinates of the Tyle(s) to removed.
     */
    fun ZoneBuilder.holes(block: FogType.Zone.ZoneHoleBuilder.() -> Unit) = mutableHoles.add(
                                                                                FogType.Zone.ZoneHoleBuilder()
                                                                                    .apply(block).build())
    /**
     * Receiver function to route and handle hole creation if the current [FogBuilder] is a [MystBuilder].
     *
     * Options:
     *
     *  - `ids`: The Zone ID(s) (i.e. the file name) of the to be removed.
     *  - `coordinates`: The Longitude/Latitude coordinates of the Zone(s) to removed.
     */
    fun MystBuilder.holes(block: FogType.Myst.MystHoleBuilder.() -> Unit) = mutableHoles.add(
                                                                                FogType.Myst.MystHoleBuilder()
                                                                                    .apply(block).build())

    /**
     * Generic function to parse and consume a [String] that describes a particular Tyle or Zone ID.
     * @param hole The String the describes the ID of the Tyle to be removed.
     * @throws FogMachineException If [hole] is in an invalid format.
     */
    fun handleStringHoleCoordinates(hole : String) {
        val atoms = hole
            .trimStart()
            .trimEnd()
            .split(",", " ", "_", "/", limit = 2)
            .map {
                it.replace(" ", "")
            }
        try {
            mutableLngLatHoles = mutableListOf(Pair(BigDecimal(atoms[0]), BigDecimal(atoms[1])))
        }
        catch (ex: Exception){
            when (ex) {
                is NumberFormatException, is IndexOutOfBoundsException -> {
                    throw FogMachineException("Error: Invalid hole format! $hole")
                }
                else -> throw(ex)
            }
        }
    }
}

/**
 * An internal [Exception] class to describe any errors that occur during operation.
 */
@FogDSL internal class FogMachineException(message: String? = null,
                                            cause: Throwable? = null) : Exception(message, cause)

/**
 * A simple and easy-to-read definition of different side length options for Tyles and Zones.
 * Each entry corresponds to a factor of 10 in Decimal Degrees (DD).
 *
 * Available values:
 *
 * - `FINE` -               0.0001 DD
 * - `MEDIUM` -             0.001 DD
 * - `COARSE` -             0.01 DD
 * - `SUPER_COARSE` -       0.1 DD
 * - `SUPER_DUPER_COARSE` - 1 DD
 */
@FogDSL enum class Coarseness {
    SUPER_DUPER_COARSE,     // Δ1
    SUPER_COARSE,           // Δ0.1
    COARSE,                 // Δ0.01
    MEDIUM,                 // Δ0.001
    FINE                    // Δ0.0001
}