package com.example

import com.example.audio.DjCurveType
import com.example.audio.MusicPlaybackManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun testCrossfadeGainsEqualPower() {
        // At t = 0.0 (start of transition): Outgoing is 100%, Incoming is 0%
        val startGains = MusicPlaybackManager.calculateCrossfadeGains(0.0f, DjCurveType.EQUAL_POWER.name)
        assertEquals(1.0f, startGains.outGain, 0.001f)
        assertEquals(0.0f, startGains.inGain, 0.001f)

        // At t = 1.0 (end of transition): Outgoing is 0%, Incoming is 100%
        val endGains = MusicPlaybackManager.calculateCrossfadeGains(1.0f, DjCurveType.EQUAL_POWER.name)
        assertEquals(0.0f, endGains.outGain, 0.001f)
        assertEquals(1.0f, endGains.inGain, 0.001f)

        // At t = 0.5 (midpoint of transition):
        // In equal power curve: gain = cos(pi/4) = sin(pi/4) ~ 0.7071
        // Sum of squares = 0.5 + 0.5 = 1.0 (constant acoustic pressure, no dip)
        val midGains = MusicPlaybackManager.calculateCrossfadeGains(0.5f, DjCurveType.EQUAL_POWER.name)
        val powerSum = (midGains.outGain * midGains.outGain) + (midGains.inGain * midGains.inGain)
        assertEquals(1.0f, powerSum, 0.02f)
        assertTrue(abs(midGains.outGain - midGains.inGain) < 0.01f)
    }

    @Test
    fun testCrossfadeGainsLinear() {
        val midGains = MusicPlaybackManager.calculateCrossfadeGains(0.5f, DjCurveType.LINEAR.name)
        assertEquals(0.5f, midGains.outGain, 0.001f)
        assertEquals(0.5f, midGains.inGain, 0.001f)
    }

    @Test
    fun testCrossfadeGainsExponential() {
        val midGains = MusicPlaybackManager.calculateCrossfadeGains(0.5f, DjCurveType.EXPONENTIAL.name)
        val powerSum = (midGains.outGain * midGains.outGain) + (midGains.inGain * midGains.inGain)
        assertEquals(1.0f, powerSum, 0.02f)
    }
}
