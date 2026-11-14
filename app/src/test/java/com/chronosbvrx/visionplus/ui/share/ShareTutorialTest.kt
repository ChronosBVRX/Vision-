package com.chronosbvrx.visionplus.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTutorialTest {

    @Test
    fun downloaderCodeIsConfiguredCorrectly() {
        assertEquals("4999823", ShareTutorialConfig.DOWNLOADER_CODE)
    }

    @Test
    fun shareMessageContainsCodeAndDownloadUrl() {
        val message = ShareTutorialConfig.buildShareMessage()
        assertTrue(message.contains("4999823"))
        assertTrue(message.contains(ShareTutorialConfig.APK_DOWNLOAD_URL))
        assertTrue(message.contains("Downloader"))
    }

    @Test
    fun whatsAppUrlHasValidPrefixAndEncoding() {
        val url = ShareTutorialConfig.buildWhatsAppUrl()
        assertTrue(url.startsWith("https://wa.me/?text="))
        assertTrue(url.contains("4999823"))
        // No debe contener espacios sin codificar
        assertTrue(!url.contains(" "))
    }
}
