package com.wayfare.ride

import com.wayfare.ride.audio.SoundAlertPlayer
import com.wayfare.ride.domain.VhayaFeedbackAction
import com.wayfare.ride.domain.VhayaFeedbackClassifier
import com.wayfare.ride.domain.VhayaFeedbackTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VhayaFeedbackAndAudioTest {

    @Test
    fun `feedback classifier infers error warning success support and info tones`() {
        assertEquals(VhayaFeedbackTone.ERROR, VhayaFeedbackClassifier.inferTone("Could not accept ride request"))
        assertEquals(VhayaFeedbackTone.ERROR, VhayaFeedbackClassifier.inferTone("Invalid PIN"))
        assertEquals(VhayaFeedbackTone.ERROR, VhayaFeedbackClassifier.inferTone("Location blocked in Settings — open Settings or search pickup manually"))

        assertEquals(VhayaFeedbackTone.WARNING, VhayaFeedbackClassifier.inferTone("Offline · rating queued and will sync automatically"))
        assertEquals(VhayaFeedbackTone.WARNING, VhayaFeedbackClassifier.inferTone("Ride cancelled"))

        assertEquals(VhayaFeedbackTone.SUCCESS, VhayaFeedbackClassifier.inferTone("Trip completed · fare recorded"))
        assertEquals(VhayaFeedbackTone.SUCCESS, VhayaFeedbackClassifier.inferTone("Card hold released"))
        assertEquals(VhayaFeedbackTone.SUCCESS, VhayaFeedbackClassifier.inferTone("Thanks for rating 5 ★"))

        assertEquals(VhayaFeedbackTone.SUPPORT, VhayaFeedbackClassifier.inferTone("Support ticket reply received"))
        assertEquals(VhayaFeedbackTone.INFO, VhayaFeedbackClassifier.inferTone("Vhaya requested · finding nearby drivers"))
    }

    @Test
    fun `feedback classifier respects explicit tone title action and assigns unique ids`() {
        val first = VhayaFeedbackClassifier.classify(
            message = "Agent responded to your ride inquiry",
            explicitTone = VhayaFeedbackTone.SUPPORT,
            title = "Ticket #42",
            actionLabel = "View",
            action = VhayaFeedbackAction.OPEN_SUPPORT
        )
        val second = VhayaFeedbackClassifier.classify(
            message = "Generic message",
            explicitTone = VhayaFeedbackTone.ERROR
        )

        assertEquals(VhayaFeedbackTone.SUPPORT, first.tone)
        assertEquals("Ticket #42", first.title)
        assertEquals("View", first.actionLabel)
        assertEquals(VhayaFeedbackAction.OPEN_SUPPORT, first.action)

        assertEquals(VhayaFeedbackTone.ERROR, second.tone)
        assertEquals(VhayaFeedbackTone.ERROR.defaultTitle, second.title)
        assertTrue(second.id > first.id)
    }

    @Test
    fun `celebration chime generates multi-note audible bounded pcm buffer`() {
        val pcm = SoundAlertPlayer.generateCelebrationChimePcm()
        assertTrue("Expected at least 10k samples for 4-note celebration chime", pcm.size > 10_000)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("Expected audible peak amplitude, got $peak", peak > 5_000)
    }
}
