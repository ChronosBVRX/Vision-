package com.chronosbvrx.visionplus.data.markers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MarkersStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MarkersStore.clearCacheForTests(context)
    }

    @Test
    fun cachedPlanSurvivesTheProcessBoundaryThroughTheDiskCache() {
        val json = """{"intro":{"s":60000,"e":90000},"credits":{"s":2500000}}"""
        MarkersStore.writeCacheForTests(context, "xtv:ABC", 3, json, System.currentTimeMillis())
        assertEquals(json, MarkersStore.cachedJson(context, "xtv:ABC", 3))
    }

    @Test
    fun cachedPlanIsPerEpisodeAndNormalizesTheRef() {
        MarkersStore.writeCacheForTests(context, "xtv:ABC", 1, """{"intro":{"s":1,"e":2}}""", System.currentTimeMillis())
        assertTrue(MarkersStore.cachedJson(context, "ABC", 1).contains("intro"))
        assertEquals("", MarkersStore.cachedJson(context, "xtv:ABC", 2))
    }

    @Test
    fun corruptOrMissingCacheIsAnEmptyPlan() {
        assertEquals("", MarkersStore.cachedJson(context, "xtv:NOEXISTE", 1))
    }
}
