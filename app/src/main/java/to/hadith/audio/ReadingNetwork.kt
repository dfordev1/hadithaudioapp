package to.hadith.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class WifiRequired : IOException("Waiting for Wi-Fi")

internal class ReadingNetwork(private val context: Context, private val store: ReadingStore) {
    fun online(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    fun onWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    suspend fun catalog(url: String): String = withContext(Dispatchers.IO) {
        val file = store.cachedText(url)
        if (file.isFile && (!online() || System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60_000L)) return@withContext file.readText()
        try {
            fetchCatalogText(url).also { ReadingStore.atomicWrite(file, it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (file.isFile) file.readText() else throw failure }
    }

    /** A paused download restarts safely; only the completed recording is offered offline. */
    suspend fun download(ref: CatalogRef, timing: HadithTiming, wifiOnly: () -> Boolean, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (wifiOnly() && !onWifi()) throw WifiRequired()
        val part = store.partial(ref)
        val connection = URL(validateTrustedMediaUrl(timing.audioUrl)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode !in 200..299) throw IOException("Audio download failed (${connection.responseCode})")
            val total = connection.contentLengthLong
            if (total > MAX_RECORDING_BYTES) throw IOException("This recording is too large to download")
            var bytes = 0L; var lastUpdate = 0L
            connection.inputStream.use { input -> part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer); if (read == -1) break
                    output.write(buffer, 0, read); bytes += read
                    if (bytes > MAX_RECORDING_BYTES) throw IOException("This recording is too large to download")
                    if (System.currentTimeMillis() - lastUpdate > 200) {
                        if (wifiOnly() && !onWifi()) throw WifiRequired()
                        progress(bytes, total); lastUpdate = System.currentTimeMillis()
                    }
                }
                output.fd.sync()
            } }
            currentCoroutineContext().ensureActive()
            if (bytes == 0L || (total > 0 && bytes != total)) throw IOException("The download was interrupted")
            store.finishDownload(ref, timing)
            progress(bytes, total)
        } finally { connection.disconnect() }
    }
    companion object { private const val MAX_RECORDING_BYTES = 512L * 1024 * 1024 }
}

internal class HadithReportRepository {
    suspend fun send(record: ReadingRecord, type: String, note: String, word: String?): String = withContext(Dispatchers.IO) {
        require(type in setOf("arabic_text", "translation", "audio_unavailable", "word_timing", "metadata", "other"))
        val connection = URL("https://hadith-error-reports.quran-wbw.workers.dev/v1/reports").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false; connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val payload = encodeJson(mapOf("collection" to record.ref.collectionSlug, "hadithNumber" to record.ref.normalizedNumber,
                "errorType" to type, "severity" to "normal", "note" to note.take(2000), "website" to "",
                "pageUrl" to record.ref.url, "timingUrl" to record.source.timingUrl, "tokenText" to word))
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IOException("Could not send the report. Please try again.")
            val result = JsonLite.parse(connection.inputStream.bufferedReader().use { it.readText() }) as? Map<*, *>
            result?.get("reportId")?.toString()?.take(8) ?: "received"
        } finally { connection.disconnect() }
    }
}
