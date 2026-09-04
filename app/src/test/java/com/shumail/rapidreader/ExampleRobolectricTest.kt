package com.shumail.rapidreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shumail.rapidreader.engine.RsvpCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rapid Reader", appName)
    }

    @Test
    fun `variable dwell math applies correct multipliers`() {
        // Punctuation multipliers
        val commaMultiplier = RsvpCalculator.calculatePauseMultiplier("word,")
        assertEquals(1.5f, commaMultiplier, 0.01f)

        val periodMultiplier = RsvpCalculator.calculatePauseMultiplier("sentence.")
        assertEquals(2.0f, periodMultiplier, 0.01f)

        val questionMultiplier = RsvpCalculator.calculatePauseMultiplier("really?")
        assertEquals(2.0f, questionMultiplier, 0.01f)

        // Long word multiplier (> 8 characters, no punctuation)
        val longWordMultiplier = RsvpCalculator.calculatePauseMultiplier("counterrevolutionaries")
        // 22 letters (>8) with no punctuation -> 1.2x
        assertEquals(1.2f, longWordMultiplier, 0.01f)

        // Combined: long word with comma -> 1.5 * 1.2 = 1.8x
        val combinedMultiplier = RsvpCalculator.calculatePauseMultiplier("counterrevolutionaries,")
        assertEquals(1.8f, combinedMultiplier, 0.01f)
    }

    @Test
    fun `calculate ORP indices correctly`() {
        // Short word
        assertEquals(0, RsvpCalculator.calculateOrpIndex("a"))
        // 2-5 letter words -> index 1
        assertEquals(1, RsvpCalculator.calculateOrpIndex("the"))
        assertEquals(1, RsvpCalculator.calculateOrpIndex("read"))
        // 6-9 letter words -> index 2
        assertEquals(2, RsvpCalculator.calculateOrpIndex("reader"))
        // 10-13 letter words -> index 3
        assertEquals(3, RsvpCalculator.calculateOrpIndex("presentation"))
    }

    @Test
    fun `parse text into RSVP words`() {
        val sample = "Rapid serial visual presentation enables fast reading."
        val words = RsvpCalculator.parseTextToRsvpWords(sample, punctuationPause = true)
        assertEquals(7, words.size)
        assertEquals("Rapid", words[0].original)
        assertTrue(words.last().pauseMultiplier > 1.0f) // Period has longer pause
    }
}
