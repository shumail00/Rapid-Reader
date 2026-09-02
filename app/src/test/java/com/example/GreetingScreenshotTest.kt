package com.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.engine.OrpColorOption
import com.example.engine.ReadingFontFamily
import com.example.engine.RsvpCalculator
import com.example.ui.components.RsvpWordDisplay
import com.example.ui.theme.RsvpAppTheme
import com.example.ui.theme.getRsvpCanvasPalette
import com.example.engine.ReadingThemeMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelTablet, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun rsvp_reader_screenshot() {
        val sampleWords = RsvpCalculator.parseTextToRsvpWords("Cognition")
        composeTestRule.setContent {
            RsvpAppTheme {
                val palette = getRsvpCanvasPalette(ReadingThemeMode.DYNAMIC, false)
                Surface(modifier = Modifier.fillMaxSize()) {
                    RsvpWordDisplay(
                        words = sampleWords,
                        palette = palette,
                        fontFamily = ReadingFontFamily.SANS_SERIF,
                        fontSizeSp = 48f,
                        orpColorOption = OrpColorOption.RED,
                        showGuides = true,
                        showContextBar = true,
                        contextBefore = "Rapid Visual",
                        contextAfter = "Presentation Engine"
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
