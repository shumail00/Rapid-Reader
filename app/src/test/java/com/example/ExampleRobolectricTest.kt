package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.RsvpCalculator
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
        assertEquals("RSVP Reader", appName)
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
