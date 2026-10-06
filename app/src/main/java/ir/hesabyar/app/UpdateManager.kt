package ir.hesabyar.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/fzhtftp-commits/HesabYar/releases/latest"

    fun checkAndOfferUpdate(context: Context) {
        Toast.makeText(context, "در حال بررسی بروزرسانی...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val update = withContext(Dispatchers.IO) { findUpdate(context) }
                if (update == null) {
                    Toast.makeText(context, "حساب‌یار به‌روز است.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("بروزرسانی جدید حساب‌یار")
                    .setMessage("نسخه ${update.version} آماده است. آیا می‌خواهید آن را دانلود و نصب کنید؟")
                    .setNegativeButton("بعداً", null)
                    .setPositiveButton("بروزرسانی") { _, _ ->
                        downloadAndInstall(context, update.url)
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "بررسی بروزرسانی انجام نشد.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findUpdate(context: Context): UpdateInfo? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HesabYar-Android")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) return null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(body)
        val remoteVersion = release.optString("tag_name").removePrefix("v")
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        if (!isNewer(remoteVersion, currentVersion)) return null

        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return UpdateInfo(remoteVersion, asset.optString("browser_download_url"))
            }
        }
        return null
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun downloadAndInstall(context: Context, apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(context, "برای نصب بروزرسانی، اجازه نصب برنامه را فعال کنید.", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(context, "در حال دانلود بروزرسانی...", Toast.LENGTH_LONG).show()
                val apk = withContext(Dispatchers.IO) { downloadApk(context, apkUrl) }
                installApk(context, apk)
            } catch (e: Exception) {
                Toast.makeText(context, "دانلود بروزرسانی انجام نشد.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadApk(context: Context, url: String): File {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HesabYar-Android")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
        val file = File(context.cacheDir, "HesabYar-update.apk")
        connection.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private data class UpdateInfo(val version: String, val url: String)
}
