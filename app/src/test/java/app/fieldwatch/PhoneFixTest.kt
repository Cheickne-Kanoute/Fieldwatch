package app.fieldwatch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneFixTest {
    @Test
    fun aFixSlightlyAheadOfTheClockStillCounts() {
        val fixAt = 10_000L
        assertTrue(phoneFixIsCurrent(fixAt, fixAt))
        assertTrue(phoneFixIsCurrent(fixAt, fixAt - 1_000L))
        assertTrue(phoneFixIsCurrent(fixAt, fixAt + 30_000L))
        assertFalse(phoneFixIsCurrent(fixAt, fixAt - 5_001L))
        assertFalse(phoneFixIsCurrent(fixAt, fixAt + 30_001L))
    }
}
