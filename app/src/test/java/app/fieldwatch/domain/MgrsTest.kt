package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MgrsTest {
    @Test
    fun forwardMatchesThePublicOneMeterVectors() {
        assertEquals("31NAA6602100000", Mgrs.forward(0.0, 0.0))
        assertEquals("31NAA6602100001", Mgrs.forward(0.00001, 0.0))
        assertEquals("11SPA7234911844", Mgrs.forward(36.2361322, -115.0820944))
        assertEquals("11S PA 72349 11844", Mgrs.format(36.2361322, -115.0820944))
    }

    @Test
    fun polarLatitudesHaveNoGrid() {
        assertNull(Mgrs.forward(88.0, 0.0))
        assertNull(Mgrs.forward(-88.0, 0.0))
        assertNull(Mgrs.format(84.0001, 0.0))
        assertNull(Mgrs.format(-80.0001, 10.0))
        assertEquals("31X", Mgrs.forward(84.0, 0.0)!!.take(3))
        assertEquals("32C", Mgrs.forward(-80.0, 10.0)!!.take(3))
    }
}
