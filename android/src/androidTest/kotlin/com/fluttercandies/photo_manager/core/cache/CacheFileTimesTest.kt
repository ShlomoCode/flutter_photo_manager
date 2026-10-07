package com.fluttercandies.photo_manager.core.cache

import android.content.ContentUris
import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.fluttercandies.photo_manager.core.entity.AssetEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CacheFileTimesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun copyingModificationTimePreservesAllNineFractionalDigits() {
        // Given an original file with a known nanosecond timestamp.
        val source = File.createTempFile("source-", ".jpg", context.cacheDir)
        val copy = File.createTempFile("copy-", ".jpg", context.cacheDir)
        val expected = longArrayOf(1577934245, 123456789)
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(CacheFileTimes.apply(source, expected))
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use {
                val stat = Os.fstat(it.fileDescriptor)
                assertArrayEquals(expected, longArrayOf(stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec))
                val time = CacheFileTimes.read(it.fd)!!

                // When copying the bytes and applying the original timestamp.
                source.copyTo(copy, overwrite = true)
                assertTrue(CacheFileTimes.apply(copy, time))
            }

            // Then Android's stat API reports all nine fractional digits on the copy.
            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use {
                val stat = Os.fstat(it.fileDescriptor)
                assertArrayEquals(expected, longArrayOf(stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec))
            }
        } finally {
            source.delete()
            copy.delete()
        }
    }

    @Test
    fun pipeDescriptorsHaveNoOriginalModificationTime() {
        // Given a provider that supplies media through a pipe.
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            // When reading its modification time.
            val time = CacheFileTimes.read(pipe[0].fd)

            // Then use the MediaStore fallback rather than the pipe's timestamp.
            assertNull(time)
        } finally {
            pipe.forEach { it.close() }
        }
    }

    @Test
    fun scopedCachePreservesTheSourceDescriptorTimestamp() {
        // Given a MediaStore item and the exact timestamp reported by its descriptor.
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "precise-timestamp-test.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        val cache = ScopedCache()
        try {
            resolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            val expected = resolver.openFileDescriptor(uri, "r")!!.use {
                val stat = Os.fstat(it.fileDescriptor)
                longArrayOf(stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec)
            }
            val asset = AssetEntity(
                id = ContentUris.parseId(uri), path = "", duration = 0, createDt = 0,
                width = 1, height = 1, type = 1, displayName = "precise-timestamp-test.jpg",
                modifiedDate = expected[0], orientation = 0
            )

            // When copying the media through ScopedCache.
            val copy = cache.getCacheFileFromEntity(context, asset, false)

            // Then preserve the descriptor's seconds and nanoseconds.
            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use {
                val stat = Os.fstat(it.fileDescriptor)
                assertArrayEquals(expected, longArrayOf(stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec))
            }
            assertArrayEquals(byteArrayOf(1, 2, 3), copy.readBytes())
        } finally {
            resolver.delete(uri, null, null)
            cache.clearFileCache(context)
        }
    }
}
