package com.custom.firelauncher

import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.Random

data class NaturePhoto(val id: String, val url: String)

/**
 * Nature photos from Wikimedia Commons "featured pictures" categories (free-licensed, no API key).
 * The TV downloads a handful at a time and keeps them in its cache so the slideshow also works offline.
 */
object NaturePhotos {

    private const val USER_AGENT = "purpledlauncher/1.0 (personal Fire TV home screen)"

    // Categories that don't exist just return nothing, so extra entries here are harmless.
    private val categories = listOf(
        "Featured pictures of landscapes",
        "Featured pictures of mountains",
        "Featured pictures of forests",
        "Featured pictures of beaches",
        "Featured pictures of lakes",
        "Featured pictures of waterfalls",
        "Featured pictures of sunsets"
    )

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun randomStart(rnd: Random): String =
        String.format(Locale.US, "%04d-%02d-%02dT00:00:00Z", 2008 + rnd.nextInt(16), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28))

    private fun fetchCategory(category: String, start: String?): List<NaturePhoto> {
        var url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
            "&generator=categorymembers&gcmtitle=" + URLEncoder.encode("Category:$category", "UTF-8") +
            "&gcmtype=file&gcmlimit=40&gcmsort=timestamp&gcmdir=newer" +
            "&prop=imageinfo&iiprop=" + URLEncoder.encode("url|size|mime", "UTF-8") + "&iiurlwidth=1920"
        if (start != null) url += "&gcmstart=" + URLEncoder.encode(start, "UTF-8")

        val pages = JSONObject(httpGet(url)).optJSONObject("query")?.optJSONObject("pages")
            ?: return emptyList()

        val found = ArrayList<NaturePhoto>()
        for (key in pages.keys()) {
            val info = pages.getJSONObject(key).optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            if (info.optString("mime") != "image/jpeg") continue
            val w = info.optInt("thumbwidth", info.optInt("width", 0))
            val h = info.optInt("thumbheight", info.optInt("height", 0))
            if (h <= 0 || w < 1280) continue
            val ratio = w.toDouble() / h
            if (ratio < 1.45 || ratio > 2.2) continue // skip portraits, squares and extreme panoramas
            val imageUrl = info.optString("thumburl").ifEmpty { info.optString("url") }
            if (imageUrl.isEmpty()) continue
            val id = "p" + (imageUrl.hashCode().toLong() and 0xffffffffL).toString(16)
            found.add(NaturePhoto(id, imageUrl))
        }
        return found
    }

    /** A shuffled list of candidate photos, or an empty list if the service couldn't be reached. */
    fun fetchList(): List<NaturePhoto> {
        val rnd = Random()
        val out = LinkedHashMap<String, NaturePhoto>()

        for (category in categories.shuffled().take(4)) {
            try {
                for (p in fetchCategory(category, randomStart(rnd))) out[p.id] = p
            } catch (e: Exception) {
                // try the next category
            }
        }
        // A random start date can land past the newest pictures; fall back to the oldest ones.
        if (out.size < 8) {
            for (category in categories.take(2)) {
                try {
                    for (p in fetchCategory(category, null)) out[p.id] = p
                } catch (e: Exception) {
                    // give up on this one
                }
            }
        }
        return out.values.shuffled()
    }

    fun download(url: String, dest: File): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 25_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", USER_AGENT)
                if (conn.responseCode !in 200..299) return false
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth < 1000) {
                tmp.delete()
                return false
            }
            return tmp.renameTo(dest)
        } catch (e: Exception) {
            tmp.delete()
            return false
        }
    }
}
