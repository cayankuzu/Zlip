package com.zlip.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zlip.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Information about an available update.
 */
data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Manages checking for GitHub releases, downloading APK and prompting package installer.
 * Preserves user data by running in-place update.
 */
object AppUpdateManager {
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/cayankuzu/Zlip/releases/latest"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    fun resetState() {
        _state.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(context: Context, silentIfUpToDate: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking

        withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Zlip-App")
                }

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    if (!silentIfUpToDate) {
                        _state.value = UpdateState.Error("Güncelleme sunucusuna bağlanılamadı (${conn.responseCode}).")
                    } else {
                        _state.value = UpdateState.Idle
                    }
                    return@withContext
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val body = json.optString("body", "")

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val name = asset?.optString("name", "") ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset?.optString("browser_download_url")
                            break
                        }
                    }
                }

                if (apkUrl == null) {
                    apkUrl = "https://github.com/cayankuzu/Zlip/releases/latest/download/Zlip.apk"
                }

                val currentVersion = BuildConfig.VERSION_NAME
                if (isNewerVersion(tagName, currentVersion)) {
                    _state.value = UpdateState.Available(
                        UpdateInfo(
                            versionName = tagName,
                            releaseNotes = body,
                            downloadUrl = apkUrl,
                        )
                    )
                } else {
                    _state.value = if (silentIfUpToDate) UpdateState.Idle else UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (!silentIfUpToDate) {
                    _state.value = UpdateState.Error(e.localizedMessage ?: "Güncelleme kontrolü başarısız oldu.")
                } else {
                    _state.value = UpdateState.Idle
                }
            }
        }
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) {
        _state.value = UpdateState.Downloading(0)

        val apkFile = withContext(Dispatchers.IO) {
            try {
                val url = URL(info.downloadUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Zlip-App")
                }

                // Handle HTTP redirects manually if necessary
                var redirectConn = conn
                var redirects = 0
                while (redirectConn.responseCode in 300..399 && redirects < 5) {
                    val newUrl = redirectConn.getHeaderField("Location") ?: break
                    redirectConn.disconnect()
                    redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "Zlip-App")
                    }
                    redirects++
                }

                val totalLength = redirectConn.contentLength
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(dir, "Zlip-${info.versionName}.apk")

                redirectConn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                val progress = ((downloaded * 100) / totalLength).toInt()
                                _state.value = UpdateState.Downloading(progress)
                            }
                        }
                        output.flush()
                    }
                }
                targetFile
            } catch (e: Exception) {
                _state.value = UpdateState.Error("APK indirilemedi: ${e.localizedMessage}")
                null
            }
        } ?: return

        _state.value = UpdateState.ReadyToInstall(apkFile)
        installApk(context, apkFile)
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(permIntent)
                return
            }

            val authority = "${context.packageName}.updateprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Yükleyici başlatılamadı: ${e.localizedMessage}")
        }
    }

    internal fun isNewerVersion(remote: String, local: String): Boolean {
        val cleanRemote = remote.trim().removePrefix("v").substringBefore("-")
        val cleanLocal = local.trim().removePrefix("v").substringBefore("-")
        if (cleanRemote.isBlank() || cleanLocal.isBlank()) return false
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
