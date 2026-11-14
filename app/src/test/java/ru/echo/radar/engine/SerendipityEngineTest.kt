package ru.echo.radar.engine

import org.junit.Assert.*
import org.junit.Test
import ru.echo.radar.discovery.BlePayload
import ru.echo.radar.model.Track

class SerendipityEngineTest {
    @Test fun aliasesAndArtistCase() {
        assertEquals(100, SerendipityEngine.resonance(Track("BAND", "One", "джаз"), Track("band", "Two", "Jazz")).score)
    }
    @Test fun unknownGenresDoNotImplySimilarity() {
        assertEquals(0, SerendipityEngine.resonance(Track("A", "One"), Track("B", "Two")).score)
    }
    @Test fun historyOrderDoesNotChangeArchetype() {
        val history = listOf(Track("A", "One", "ambient"), Track("B", "Two", "jazz"))
        assertEquals(SerendipityEngine.archetype(history), SerendipityEngine.archetype(history.reversed()))
    }
    @Test fun payloadFitsLegacyAdvertising() {
        val payload = BlePayload.encode("session", "artist|title")
        assertEquals(17, payload.size)
        assertTrue(BlePayload.valid(payload))
        assertArrayEquals(payload, BlePayload.encode("session", "artist|title"))
        assertFalse(payload.contentEquals(BlePayload.encode("other", "artist|title")))
    }
}
