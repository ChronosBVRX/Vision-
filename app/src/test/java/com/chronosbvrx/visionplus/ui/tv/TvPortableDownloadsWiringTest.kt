package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de cableado de la biblioteca portátil: la pantalla de Descargas debe
 * escanear la USB, ofrecer el PIN y mostrar el PIN de la TV de origen.
 */
class TvPortableDownloadsWiringTest {

    private fun source(path: String): String {
        val file = File("app/src/main/java/com/chronosbvrx/visionplus/$path")
        return if (file.exists()) file.readText() else File("src/main/java/com/chronosbvrx/visionplus/$path").readText()
    }

    @Test
    fun downloadsScreenScansUsbAndOffersPin() {
        val text = source("ui/tv/TvDownloadsScreen.kt")
        assertTrue(text.contains("PortableLibrary.scanVolumes(context)"))
        assertTrue(text.contains("PortableLibrary.importUnlocked(context"))
        assertTrue(text.contains("TvPortablePinDialog("))
        assertTrue(text.contains("TvPortablePinShowDialog("))
        assertTrue(text.contains("PortableLibrary.pinForDownload(context, item)"))
    }

    @Test
    fun managerWritesPortableDownloadsAndFinalizesThem() {
        val text = source("data/offline/OfflineDownloadManager.kt")
        assertTrue(text.contains("PortableLibrary.isEnabled(context)"))
        assertTrue(text.contains("PortableLibrary.finalizeDownload("))
        assertTrue(text.contains("portable = item.portable"))
    }

    @Test
    fun playerReadsPortableContainer() {
        val text = source("data/offline/EncryptedVaultDataSource.kt")
        assertTrue(text.contains("PortableVaultCrypto.readHeader(raf)"))
        assertTrue(text.contains("resolvePortableContentKey("))
        assertTrue(text.contains("PortableLibrary.getLibraryKey(context, libraryId)"))
    }
}
