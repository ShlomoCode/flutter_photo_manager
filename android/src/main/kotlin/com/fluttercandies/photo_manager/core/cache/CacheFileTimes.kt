package com.fluttercandies.photo_manager.core.cache

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.fluttercandies.photo_manager.util.LogUtils
import java.io.File
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.Q)
internal object CacheFileTimes {
    private val available = try {
        System.loadLibrary("photo_manager_file_times")
        true
    } catch (error: UnsatisfiedLinkError) {
        LogUtils.error("Cannot load native cache timestamp support", error)
        false
    }

    fun read(fd: Int): LongArray? {
        if (!available) return null
        return try {
            readModificationTime(fd)
        } catch (error: IOException) {
            LogUtils.error("Cannot read original file modification time", error)
            null
        }
    }

    fun apply(file: File, time: LongArray): Boolean {
        if (!available) return false
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use {
                setModificationTime(it.fd, time[0], time[1])
            }
            true
        } catch (error: IOException) {
            LogUtils.error("Cannot preserve precise cache modification time", error)
            false
        }
    }

    // Android's Java timestamp setters truncate subsecond precision.
    @JvmStatic
    private external fun readModificationTime(fd: Int): LongArray?

    @JvmStatic
    private external fun setModificationTime(fd: Int, seconds: Long, nanoseconds: Long)
}
