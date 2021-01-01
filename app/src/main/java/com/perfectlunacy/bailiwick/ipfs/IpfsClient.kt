package com.perfectlunacy.bailiwick.ipfs

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.io.File

class IpfsClient(private val context: Context) {
    var daemonProc: Process? = null

    /*
     * Launch the IPFS client process in daemon mode
     *
     * TODO: Manage this in an Android Service
     */
    fun startDaemon() {
        val cmd = "$IpfsExecutablePath daemon"
        daemonProc = Runtime.getRuntime().exec(cmd)
    }

    /*
     * Best effort "kill -15" of the ipfs daemon proc
     */
    fun stopDaemon() {
        daemonProc?.destroy()
    }

    /*
     * Returns the path to the native library directory, sans architecture
     * e.g. truncates /data/app/<package>/lib/armeabi-v7a to /data/app/<package>/lib
     */
    @VisibleForTesting
    val libDir = File(context.applicationInfo.nativeLibraryDir).parentFile!!.absolutePath

    /*
     * Returns the path to the ipfs executable within the native libs directory
     * e.g. /data/app/<package>/lib/arm64-v8a/libipfs.so
     *
     * "But wait", you say, "Why are you naming an executable file libipfs.so instead of just 'ipfs'?"
     * Well, because Android Q+ won't let you run external executables that are in a writable location (like assets/)
     * So to get around that, you need to put them in an unwritable location, e.g. the /libs or /jniLibs folders.
     *
     * However, only files named lib<foo>.so (or wrap.sh) will be copied into the APK.
     *
     * So... you name your file lib<foo>.so and put it in libs.
     *
     * ...and then theoretically, you can launch it with System.exec()
     *
     * This is all complicated by the fact that this currently doesn't work.
     *
     * References:
     * https://stackoverflow.com/questions/60370424/permission-is-denied-using-android-q-ffmpeg-error-13-permission-denied/60370584#60370584
     * https://issuetracker.google.com/issues/152645643
     */
    @VisibleForTesting
    val IpfsExecutablePath = "$libDir/$ipfsArch/libipfs.so"

    private val ipfsArch: String
        get() = Build.SUPPORTED_ABIS.firstOrNull { abi -> File(libDir, abi).exists() } ?: "unknown"

}