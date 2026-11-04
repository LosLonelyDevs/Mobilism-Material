package org.mobilism.material.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme

class UserProfileTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = UserProfile(ProfileReference(id = 99), "Example Member", null,
        setOf(AccountRole.Developer), "Developer", listOf("Developers", "Registered users"),
        "A short biography.", mapOf("Device" to "Example phone"), "Jan 1, 2020", null, "123", "250.00",
        listOf(ProfileContact("Telegram", "https://telegram.me/example_channel", ProfileContactKind.Telegram),
            ProfileContact("WhatsApp", "https://whatsapp.com/channel/example", ProfileContactKind.WhatsApp)))

    @Test fun contactButtonsOpenExactUrlsAndDeveloperBadgeFitsANarrowPhone() {
        val opened = mutableListOf<String>()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Box(Modifier.width(320.dp)) { UserProfileContent(profile, onOpenContact = { opened += it }) }
        } }
        compose.onNodeWithText("Developer").assertIsDisplayed()
        compose.onNodeWithText("Join Telegram").performScrollTo().performClick()
        compose.onNodeWithText("Join WhatsApp").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(profile.contacts.map { it.url }, opened) }
    }

    @Test fun tappingAUsernameUsesTheProfileHandler() {
        var opened: ProfileReference? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalProfileHandler provides { opened = it }) { UsernameLink("Example Member") }
        } }
        compose.onNodeWithText("Example Member").performClick()
        compose.runOnIdle { assertEquals("Example Member", opened?.username) }
    }

    @Test fun contributorRankRemainsVisibleAsItsOwnBadge() {
        compose.setContent { MobilismTheme(reduceMotion = true) {
            UserProfileContent(profile.copy(roles = setOf(AccountRole.Member), rank = "Major Android Releaser",
                groups = listOf("Major Android Releaser", "Registered users")))
        } }
        compose.onNodeWithText("Major Android Releaser").assertIsDisplayed()
    }
}
