package eu.kanade.tachiyomi.animeextension.all.iccftp

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import extensions.utils.Source
import extensions.utils.delegate
import extensions.utils.get
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class IccFtp : Source(), ConfigurableAnimeSource {

    override val name = "ICC FTP"
    override val baseUrl = "http://10.16.100.244"
    override val lang = "all"
    override val supportsLatest = true
    override val id: Long = 84726193058274619L

    private var sessionId: String by preferences.delegate("session_id", "")

    private val sessionInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        if (originalUrl.encodedPath == "/" && originalUrl.querySize == 0) {
            return@Interceptor chain.proceed(originalRequest)
        }

        val newUrl = if (originalUrl.queryParameter("session") == null && !originalUrl.encodedPath.contains("command.php")) {
            if (sessionId.isBlank()) fetchSessionId()
            originalUrl.newBuilder().addQueryParameter("session", sessionId).build()
        } else {
            originalUrl
        }

        val request = originalRequest.newBuilder().url(newUrl).build()
        val response = chain.proceed(request)

        if (response.request.url.encodedPath.contains("vfw.php") || (response.code == 302 && response.header("Location")?.contains("vfw.php") == true)) {
            response.close()
            sessionId = "" 
            fetchSessionId()
            val retryUrl = originalUrl.newBuilder().addQueryParameter("session", sessionId).build()
            return@Interceptor chain.proceed(originalRequest.newBuilder().url(retryUrl).build())
        }

        response
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(sessionInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Synchronized
    private fun fetchSessionId() {
        if (sessionId.isNotBlank()) return
        try {
            val response = client.newCall(GET(baseUrl)).execute()
            val finalUrl = response.request.url
            response.close()
            val session = finalUrl.queryParameter("session")
            if (session != null) sessionId = session
        } catch (e: Exception) {
            // Log error
        }
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val request = if (page == 1) {
            GET("$baseUrl/dashboard.php")
        } else {
            val body = FormBody.Builder().add("cpage", page.toString()).build()
            POST("$baseUrl/command.php", body = body)
        }
        val response = client.newCall(request).execute()
        return parseAnimeList(response.asJsoup(), page < 50) // Guessing max pages
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isNotBlank()) {
            val dashboard = client.newCall(GET("$baseUrl/dashboard.php")).execute().asJsoup()
            val token = dashboard.selectFirst("input[name=token]")?.attr("value") ?: ""
            val body = FormBody.Builder().add("token", token).add("psearch", query).build()
            val response = client.newCall(POST("$baseUrl/dashboard.php", body = body)).execute()
            return parseAnimeList(response.asJsoup(), false)
        }

        var category = ""
        filters.forEach { filter ->
            if (filter is CategoryFilter) category = filter.toValue()
        }

        val url = if (category.isNotBlank()) "$baseUrl/dashboard.php?category=$category" else "$baseUrl/dashboard.php"
        val response = client.newCall(GET(url)).execute()
        return parseAnimeList(response.asJsoup(), false)
    }

    private fun parseAnimeList(document: Document, hasNext: Boolean): AnimesPage {
        val items = document.select("div.item, div.post, div.post-item") 
        val animeList = items.mapNotNull { item ->
            val link = item.selectFirst("a") ?: item.parent()?.takeIf { it.tagName() == "a" }
            val url = link?.attr("href") ?: ""
            val img = item.selectFirst("div.img, img")?.let {
                it.attr("style").substringAfter("url('").substringBefore("')").ifEmpty { it.attr("src") }
            }
            val title = item.selectFirst("div.title, h3, span.title")?.text() ?: item.attr("alt")

            if (url.isNotEmpty() && !title.isNullOrEmpty()) {
                SAnime.create().apply {
                    this.url = url
                    this.title = title
                    this.thumbnail_url = if (!img.isNullOrEmpty()) {
                        if (img.startsWith("http")) img else "$baseUrl/$img"
                    } else null
                }
            } else null
        }
        return AnimesPage(animeList, hasNext && animeList.isNotEmpty())
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime.apply { initialized = true }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            name = "Play Movie/TV"
            url = anime.url
            episode_number = 1F
        })
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val url = if (episode.url.startsWith("http")) episode.url else "$baseUrl/${episode.url}"
        val document = client.newCall(GET(url)).execute().asJsoup()
        val videoUrl = document.select("video source").attr("src").ifEmpty { document.select("video").attr("src") }
        if (videoUrl.isNotEmpty()) {
            val absoluteVideoUrl = if (videoUrl.startsWith("http")) videoUrl else "$baseUrl/$videoUrl"
            return listOf(Video(absoluteVideoUrl, "Direct", absoluteVideoUrl))
        }
        throw IOException("Video not found")
    }

    override fun getFilterList() = AnimeFilterList(CategoryFilter())

    private class CategoryFilter : AnimeFilter.Select<String>("Category", arrayOf(
        "All", "3D", "4K", "Animated", "Anime", "Bangla (BD)", "Bangla (Kolkata)", "Chinese Movies", 
        "Documentaries", "Dual Audio", "English Movies", "Exclusive Full-HD", "Hindi Movies", 
        "Indonesian Movies", "Japanese Movies", "Korean Movies", "South Indian Movies", "WWE",
        "Bangla Drama", "Serials (Anime)", "Serials (English)", "Serials (Hindi)"
    )) {
        private val ids = arrayOf(
            "", "9", "74", "33", "83", "59", "60", "76", 
            "41", "43", "19", "44", "2", 
            "79", "80", "75", "73", "52",
            "34", "78", "36", "37"
        )
        fun toValue() = ids[state]
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}