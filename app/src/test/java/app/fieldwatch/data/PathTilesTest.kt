package app.fieldwatch.data

import app.fieldwatch.domain.MapBasemap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTilesTest {
    @Test
    fun zoomTightensForAShortSpanAndOpensForADrive() {
        assertTrue(PathTiles.zoomFor(80.0, 28.5) >= 15)
        assertTrue(PathTiles.zoomFor(12_000.0, 28.5) in 8..14)
    }

    @Test
    fun expandToPlotFillsAWidePlotForATallPath() {
        val box = PathTiles.expandToPlot(28.780, 28.790, -81.370, -81.3695)
        val latSpan = box[1] - box[0]
        val lonSpan = box[3] - box[2]
        val yM = latSpan * 110_540.0
        val xM = lonSpan * 111_320.0 * kotlin.math.cos(Math.toRadians(28.785))
        assertTrue(xM / yM in 1.5..1.9)
    }

    @Test
    fun coverageKeyChangesWhenTheViewLeavesItsTiles() {
        val here = PathTiles.coverageKey(28.78, 28.79, -81.38, -81.37)
        val away = PathTiles.coverageKey(29.10, 29.11, -81.10, -81.09)
        assertNotEquals("off", here)
        assertNotEquals(here, away)
        assertEquals(here, PathTiles.coverageKey(28.78, 28.79, -81.38, -81.37))
    }

    @Test
    fun aPrintedWalkUsesTheNextZoom() {
        val south = 28.780
        val north = 28.784
        val west = -81.370
        val east = -81.366
        val screen = PathTiles.plannedZoom(south, north, west, east)
        val print = PathTiles.plannedZoom(south, north, west, east, forPrint = true)
        assertEquals((screen + 2).coerceAtMost(19), print)
        assertTrue(print <= 19)
    }

    @Test
    fun coverageKeyNamesTheMap() {
        val osm = PathTiles.coverageKey(28.78, 28.79, -81.38, -81.37, MapBasemap.OSM)
        val imagery = PathTiles.coverageKey(28.78, 28.79, -81.38, -81.37, MapBasemap.USGS_IMAGERY)
        assertTrue(osm.startsWith("OSM:"))
        assertTrue(imagery.startsWith("USGS_IMAGERY:"))
        assertNotEquals(osm, imagery)
    }

    @Test
    fun usgsPrintStopsAtTheLastRealZoom() {
        val south = 28.780
        val north = 28.784
        val west = -81.370
        val east = -81.366
        val imagery = PathTiles.plannedZoom(
            south, north, west, east, forPrint = true, basemap = MapBasemap.USGS_IMAGERY,
        )
        val topo = PathTiles.plannedZoom(
            south, north, west, east, forPrint = true, basemap = MapBasemap.USGS_TOPO,
        )
        assertTrue(imagery in 8..16)
        assertTrue(topo in 8..16)
    }

    @Test
    fun tileUrlsUseEachServicesOrder() {
        assertEquals(
            "https://tile.openstreetmap.org/12/1140/1536.png",
            MapBasemap.OSM.tileUrl(12, 1140, 1536),
        )
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/12/1536/1140",
            MapBasemap.USGS_IMAGERY.tileUrl(12, 1140, 1536),
        )
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/12/1536/1140",
            MapBasemap.USGS_TOPO.tileUrl(12, 1140, 1536),
        )
    }

    @Test
    fun aSingleFixStillRequestsANeighborhoodOfTiles() {
        val box = PathTiles.expandToPlot(28.780, 28.780, -81.370, -81.370)
        val yM = (box[1] - box[0]) * 110_540.0
        assertTrue(yM >= 400.0)
    }
}
