package com.hilight.studio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Why a downloaded update was refused before the system installer was ever shown. */
internal enum class UpdateRejection { NOT_AN_APK, WRONG_PACKAGE, NOT_NEWER, WRONG_SIGNER }

/**
 * Decides whether a downloaded APK may be handed to the system installer.
 *
 * Android would refuse a differently signed update to an installed package anyway, but only after
 * showing the user an install prompt for it. Checking first means a tampered or mismatched asset
 * never reaches that prompt, and a same-version or older build is not offered as an "update".
 *
 * Signers are compared as SHA-256 certificate digests. The download must be signed by the exact
 * certificate set the installed app is signed with, or — after a key rotation — carry the installed
 * signer in its own rotation history.
 */
internal fun updateRejection(
    installedPackage: String,
    installedVersionCode: Long,
    installedSigners: Set<String>,
    archivePackage: String?,
    archiveVersionCode: Long,
    archiveSigners: Set<String>,
    archiveSignerHistory: Set<String>,
): UpdateRejection? = when {
    archivePackage == null -> UpdateRejection.NOT_AN_APK
    archivePackage != installedPackage -> UpdateRejection.WRONG_PACKAGE
    archiveVersionCode <= installedVersionCode -> UpdateRejection.NOT_NEWER
    installedSigners.isEmpty() || archiveSigners.isEmpty() -> UpdateRejection.WRONG_SIGNER
    archiveSigners == installedSigners -> null
    installedSigners.all { it in archiveSignerHistory } -> null
    else -> UpdateRejection.WRONG_SIGNER
}

/** Downloads a release APK, verifies it, and opens the system installer for it. */
internal object UpdateInstaller {
    private const val MAX_APK_BYTES = 64L * 1024 * 1024
    private const val UPDATE_DIR = "updates"
    private const val UPDATE_FILE = "HiLightStudioUpdate.apk"

    private val downloading = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    val isDownloading: Boolean get() = downloading.get()

    /**
     * Starts one download at a time. [onProgress] runs on the main thread with true when the
     * download starts and false once it has finished, failed, or been handed to the installer.
     */
    fun downloadAndInstall(ctx: Context, apkUrl: String, onProgress: (Boolean) -> Unit) {
        if (!downloading.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        onProgress(true)
        Thread({
            val result = runCatching { download(app, apkUrl) }
            main.post {
                downloading.set(false)
                onProgress(false)
                val apk = result.getOrNull()
                if (apk == null || !launchInstaller(app, apk)) {
                    Toast.makeText(app, R.string.setup_updates_download_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }, "hilight-update").apply { isDaemon = true }.start()
    }

    private fun download(app: Context, apkUrl: String): File {
        val url = URL(apkUrl)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS required" }
        val dir = File(app.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(dir, UPDATE_FILE)
        target.delete()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            // A redirect may only land on HTTPS; HttpURLConnection never follows a protocol change,
            // but the final URL is checked rather than assumed.
            check(connection.url.protocol.equals("https", ignoreCase = true)) { "HTTPS required" }
            val declared = connection.contentLengthLong
            check(declared <= MAX_APK_BYTES) { "APK exceeds maximum size" }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_APK_BYTES) { "APK exceeds maximum size" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            verify(app, target)
            return target
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(app: Context, apk: File) {
        val pm = app.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val installed = pm.getPackageInfo(app.packageName, flags)
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        val rejection = updateRejection(
            installedPackage = installed.packageName,
            installedVersionCode = installed.longVersionCode,
            installedSigners = currentSigners(installed),
            archivePackage = archive?.packageName,
            archiveVersionCode = archive?.longVersionCode ?: 0L,
            archiveSigners = archive?.let(::currentSigners).orEmpty(),
            archiveSignerHistory = archive?.let(::signerHistory).orEmpty(),
        )
        if (rejection != null) throw SecurityException("update rejected: $rejection")
    }

    private fun launchInstaller(app: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }.isSuccess

    private fun currentSigners(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().mapTo(HashSet()) { sha256(it.toByteArray()) }

    private fun signerHistory(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        // History is only meaningful for a single signer that has rotated its key.
        if (signing.hasMultipleSigners()) return emptySet()
        return signing.signingCertificateHistory.orEmpty().mapTo(HashSet()) { sha256(it.toByteArray()) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
