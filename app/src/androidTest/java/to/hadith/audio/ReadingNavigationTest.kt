package to.hadith.audio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test

class ReadingNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appearancePersistsAndNavigationSurvivesActivityRecreation() {
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Sepia appearance").performClick()
        compose.onNodeWithContentDescription("Dark appearance").performClick()
        compose.waitUntil(5000) { ReadingStore(File(compose.activity.filesDir, "reading")).readLibrary().settings.appearance == ReadingAppearance.DARK }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Dark appearance").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A space of your own").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Saved", substring = false).performClick()
        compose.onNodeWithText("Keep a passage close").assertIsDisplayed()
        compose.onNodeWithText("Explore collections").performClick()
        compose.onNodeWithText("Sahih al-Bukhari", substring = false).assertIsDisplayed()
    }
}
