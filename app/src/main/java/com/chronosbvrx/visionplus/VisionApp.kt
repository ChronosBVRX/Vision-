package com.chronosbvrx.visionplus

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.size.Precision
import java.io.File

import coil.ImageLoaderFactory

/**
 * Vision+ process-wide image pipeline, tuned for television boxes (Chromecast, Fire TV): no
 * crossfade (fewer per-frame allocations), safe software bitmaps on TV and low-end devices
 * (eliminates GPU hardware buffer exhaustion and SIGSEGV in libhwui during rapid D-pad scrolling),
 * generous memory cache and big disk cache so rails, channel lists and sports rows repaint smoothly.
 * Implements [ImageLoaderFactory] so disk cache and image pipeline are constructed lazily on demand
 * without blocking Application.onCreate() during cold start.
 */
class VisionApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        com.chronosbvrx.visionplus.data.offline.OfflineRegistry.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val isTv = DeviceKind.isTelevision(this)
        val isLowEnd = DevicePerformance.isLowEndDevice(this)
        val useModestMemory = isTv || isLowEnd
        val memPercent = if (useModestMemory) 0.15 else 0.25
        return ImageLoader.Builder(this)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .allowRgb565(useModestMemory)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
