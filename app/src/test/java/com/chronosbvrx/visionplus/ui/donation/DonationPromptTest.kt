package com.chronosbvrx.visionplus.ui.donation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DonationPromptTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("vision_donation", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun thePromptIsDailyAndCanBeDisabledForever() {
        assertTrue(DonationPrompt.shouldShow(context))
        DonationPrompt.markShown(context)
        assertFalse("ya se mostró hoy", DonationPrompt.shouldShow(context))
        DonationPrompt.never(context)
        assertFalse("no volver a mostrar", DonationPrompt.shouldShow(context))
    }

    @Test
    fun theDonationUrlIsPublicHttpsAndTheQrIsGenerated() {
        assertTrue(DONATION_URL.startsWith("https://"))
        assertTrue(DONATION_URL.endsWith("/apoyar"))
        assertNotNull(donationQrBitmap(300))
    }
}
