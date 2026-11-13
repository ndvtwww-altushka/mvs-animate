package com.mvs.animate.data

import com.mvs.animate.model.SourceAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object WikimediaClient {
    suspend fun search(query: String): List<SourceAsset> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Enter a search phrase" }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=16&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=720&format=json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "MVSAnimate/0.1 (Android creative editor)")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Commons search failed: HTTP $code")
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
            val results = ArrayList<SourceAsset>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val meta = info.optJSONObject("extmetadata") ?: JSONObject()
                results += SourceAsset(
                    title = page.optString("title").removePrefix("File:"),
                    thumbnailUrl = info.optString("thumburl").takeIf(String::isNotBlank),
                    pageUrl = info.optString("descriptionurl").ifBlank { "https://commons.wikimedia.org/wiki/${URLEncoder.encode(page.optString("title"), "UTF-8")}" },
                    license = stripHtml(meta.optJSONObject("LicenseShortName")?.optString("value").orEmpty()).ifBlank { "Check license on source page" },
                    author = stripHtml(meta.optJSONObject("Artist")?.optString("value").orEmpty()).ifBlank { "Author not listed" },
                )
            }
            results
        } finally { conn.disconnect() }
    }

    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}
