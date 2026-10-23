package com.chronosbvrx.visionplus.home

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * El proceso del launcher. Coil se configura para una pantalla que vive siempre encendida:
 * caché de memoria contenida (Chromecast HD tiene 1.5 GB) y disco propio, sin crossfade.
 */
class HomeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.12)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "home_images").apply { mkdirs() })
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        .build()

    companion object {
        lateinit var instance: HomeApp
            private set

        fun preferences(context: Context) = com.chronosbvrx.visionplus.home.data.LauncherPrefs(context)
    }
}
