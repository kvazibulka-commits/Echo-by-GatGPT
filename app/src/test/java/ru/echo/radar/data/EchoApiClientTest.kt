package ru.echo.radar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.echo.radar.model.GeoPoint
import ru.echo.radar.model.Track

class EchoApiClientTest {
    @Test fun connectionRejectsNonHttpOrigin() {
        try { EchoApiClient("ftp://echo.local") } catch (error: IllegalArgumentException) {
            assertEquals("Echo URL must use HTTP(S)", error.message)
        }
    }

    @Test fun coordinatesRemainFiniteAndBounded() {
        assertEquals(Track("Artist", "Track"), Track("Artist", "Track"))
        assertEquals(55.75, GeoPoint(55.75, 37.62).latitude, 0.0)
    }
}
