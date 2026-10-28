package com.netmirror.netmirrortv.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Handles downloading APK updates with byte/percent progress and triggering
 * the Android system package installer on Android TV.
 */
object ApkUpdateInstaller {

    private const val TAG = "ApkUpdateInstaller"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Downloads an APK from [apkUrl], emits progress (0-100), and on completion
     * launches the system package installer.
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onError: (message: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = apkUrl.trim()
            if (trimmedUrl.isEmpty() || !trimmedUrl.startsWith("http", ignoreCase = true)) {
                onError("Invalid update download link.")
                return@withContext
            }

            Log.d(TAG, "Starting APK download from: $trimmedUrl")

            // Immediate initial emission so UI instantly shows the downloading state
            withContext(Dispatchers.Main) {
                onProgress(0, 0L, -1L)
            }

            val request = Request.Builder()
                .url(trimmedUrl)
                .addHeader("User-Agent", Constants.DEFAULT_USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                onError("Download failed: Server returned HTTP ${response.code}")
                return@withContext
            }

            val body = response.body
            if (body == null) {
                onError("Download failed: Empty response body.")
                return@withContext
            }

            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            val apkFile = File(updatesDir, "NetmirrorTV_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var downloadedBytes = 0L
            val buffer = ByteArray(16 * 1024)
            var lastReportedPercent = -1
            var lastReportedTime = System.currentTimeMillis()

            body.byteStream().use { input: InputStream ->
                FileOutputStream(apkFile).use { output: FileOutputStream ->
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1 // indeterminate
                        }

                        val currentTime = System.currentTimeMillis()
                        // Update on every percentage change, or every 200ms for continuous live MB count
                        if (percent != lastReportedPercent || (currentTime - lastReportedTime >= 200)) {
                            lastReportedPercent = percent
                            lastReportedTime = currentTime
                            withContext(Dispatchers.Main) {
                                onProgress(percent, downloadedBytes, totalBytes)
                            }
                        }
                        bytesRead = input.read(buffer)
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(100, downloadedBytes, totalBytes)
                installApk(context, apkFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "APK download error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError(e.localizedMessage ?: "Failed to download update.")
            }
        }
    }

    /**
     * Triggers the Android Package Installer for the downloaded APK file.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() <= 0) {
                Log.e(TAG, "APK file does not exist or is empty.")
                return
            }

            // Check Unknown Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.i(TAG, "Requesting unknown sources permission.")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}", e)
        }
    }
}
