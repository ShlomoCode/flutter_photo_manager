#include <errno.h>
#include <jni.h>
#include <string.h>
#include <sys/stat.h>

static void throw_io_error(JNIEnv *env) {
    const int error = errno;
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) {
        (*env)->ThrowNew(env, type, strerror(error));
    }
}

JNIEXPORT jlongArray JNICALL
Java_com_fluttercandies_photo_1manager_core_cache_CacheFileTimes_readModificationTime(
    JNIEnv *env, jclass type, jint fd) {
    (void)type;
    struct stat info;
    if (fstat(fd, &info) != 0) {
        throw_io_error(env);
        return NULL;
    }
    // Providers can return pipes, which have no original file timestamp.
    if (!S_ISREG(info.st_mode)) {
        return NULL;
    }
    const jlong values[] = {info.st_mtim.tv_sec, info.st_mtim.tv_nsec};
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result != NULL) {
        (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_fluttercandies_photo_1manager_core_cache_CacheFileTimes_setModificationTime(
    JNIEnv *env, jclass type, jint fd, jlong seconds, jlong nanoseconds) {
    (void)type;
    const struct timespec times[] = {
        {.tv_sec = 0, .tv_nsec = UTIME_OMIT},
        {.tv_sec = seconds, .tv_nsec = nanoseconds},
    };
    if (futimens(fd, times) != 0) {
        throw_io_error(env);
    }
}
