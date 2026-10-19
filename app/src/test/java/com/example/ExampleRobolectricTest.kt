package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.DjCurveType
import com.example.audio.MusicPlaybackManager
import com.example.audio.RepeatMode
import com.example.data.MusicTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MusicPlay", appName)
    }

    @Test
    fun `test default crossfade settings and persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = MusicPlaybackManager.getInstance()
        manager.setContext(context)

        // Default crossfade duration must be 8 seconds per user requirement
        assertEquals(8, manager.crossfadeSec.value)
        assertTrue(manager.crossfadeEnabled.value)
        assertTrue(manager.djModeEnabled.value)
        assertEquals(DjCurveType.EQUAL_POWER.name, manager.djCurveType.value)

        // Test updating and clamping
        manager.setCrossfadeSec(12)
        assertEquals(12, manager.crossfadeSec.value)

        manager.setCrossfadeSec(25) // Clamped to 20
        assertEquals(20, manager.crossfadeSec.value)

        manager.setCrossfadeSec(-5) // Clamped to 0
        assertEquals(0, manager.crossfadeSec.value)

        manager.setCrossfadeSec(8)
        assertEquals(8, manager.crossfadeSec.value)
    }

    @Test
    fun `test queue navigation with repeat modes`() {
        val manager = MusicPlaybackManager.getInstance()
        val track1 = MusicTrack(id = 1, title = "Song A", artist = "Artist 1", album = "Album 1", genre = "Pop", year = "2026", trackNumber = "1", durationSec = 180, lyrics = "", folder = "Music")
        val track2 = MusicTrack(id = 2, title = "Song B", artist = "Artist 2", album = "Album 2", genre = "Rock", year = "2026", trackNumber = "2", durationSec = 200, lyrics = "", folder = "Music")
        val track3 = MusicTrack(id = 3, title = "Song C", artist = "Artist 3", album = "Album 3", genre = "Jazz", year = "2026", trackNumber = "3", durationSec = 220, lyrics = "", folder = "Music")

        manager.setQueue(listOf(track1, track2, track3), 0)

        // RepeatMode.ALL at index 0 -> next is index 1
        assertEquals(1, manager.getNextTrackIndex())
        assertEquals(track2.id, manager.getNextTrack()?.id)

        // At index 2 in RepeatMode.ALL -> wraps to index 0
        manager.currentIndex = 2
        assertEquals(0, manager.getNextTrackIndex())
        assertEquals(track1.id, manager.getNextTrack()?.id)

        // RepeatMode.ONE -> next track is the same track (seamless loop crossfade)
        while (manager.repeatMode.value != RepeatMode.ONE) {
            manager.toggleRepeatMode()
        }
        manager.currentIndex = 1
        assertEquals(1, manager.getNextTrackIndex())
        assertEquals(track2.id, manager.getNextTrack()?.id)

        // RepeatMode.NONE at last track -> returns -1 / null (no crossfade past end)
        while (manager.repeatMode.value != RepeatMode.NONE) {
            manager.toggleRepeatMode()
        }
        manager.currentIndex = 2
        assertEquals(-1, manager.getNextTrackIndex())
        assertNull(manager.getNextTrack())
    }
}
