package com.fluttercandies.photo_manager.core.cache

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.fluttercandies.photo_manager.core.entity.AssetEntity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ScopedCacheTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val cache = ScopedCache()
    private var source: Uri? = null

    @After
    fun cleanUp() {
        source?.let { resolver.delete(it, null, null) }
        cache.clearFileCache(context)
    }

    @Test
    fun cachedMediaKeepsOriginalModificationTime() {
        // Given media whose original modification time predates the cache copy.
        val bytes = byteArrayOf(1, 2, 3)
        val asset = createAsset(bytes, 1577934245)

        // When copying the media into the cache.
        val file = cache.getCacheFileFromEntity(context, asset, false)

        // Then preserve the original time and bytes, including on a cache hit.
        assertEquals(1577934245000L, file.lastModified())
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(file, cache.getCacheFileFromEntity(context, asset, false))
        assertEquals(1577934245000L, file.lastModified())
    }

    @Test
    fun cachedMediaKeepsAnOriginalModificationTimeOfZero() {
        // Given media with an original modification time at the Unix epoch.
        val asset = createAsset(byteArrayOf(1, 2, 3), 0)

        // When copying the media into the cache.
        val file = cache.getCacheFileFromEntity(context, asset, false)

        // Then preserve zero as a valid timestamp.
        assertEquals(0L, file.lastModified())
    }

    private fun createAsset(bytes: ByteArray, modifiedSeconds: Long): AssetEntity {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "timestamp-test.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        source = uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return AssetEntity(
            id = ContentUris.parseId(uri), path = "", duration = 0, createDt = 0,
            width = 1, height = 1, type = 1, displayName = "timestamp-test.jpg",
            modifiedDate = modifiedSeconds, orientation = 0
        )
    }
}
