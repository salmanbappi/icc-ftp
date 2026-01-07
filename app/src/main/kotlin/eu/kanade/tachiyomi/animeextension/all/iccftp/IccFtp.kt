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
import extensions.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class IccFtp : Source(), ConfigurableAnimeSource {

    override val name = "ICC FTP"
    override val baseUrl = "http://10.16.100.244"
    override val lang = "all"
    override val supportsLatest = true
    override val id: Long = 84726193058274619L

    override val json = Json { ignoreUnknownKeys = true }
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
        } catch (e: Exception) {}
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/index.php?category=0" else "$baseUrl/index.php?category=0&pageno=$page"
        val response = client.newCall(GET(url)).execute()
        return parseAnimeList(response.asJsoup(), true)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isNotBlank()) {
            val body = "cSearch=$query".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val response = client.newCall(POST("$baseUrl/command.php", body = body)).execute()
            val searchJson = response.parseAs<List<SearchJsonItem>>(json)
            val animeList = searchJson.map { item ->
                SAnime.create().apply {
                    this.url = "player.php?play=${item.id}"
                    this.title = item.name ?: ""
                    this.thumbnail_url = "$baseUrl/files/${item.image}"
                }
            }
            return AnimesPage(animeList, false)
        }

        var category = "0"
        filters.forEach { if (it is CategoryFilter) category = it.toValue() }
        
        val url = if (page == 1) "$baseUrl/index.php?category=$category" else "$baseUrl/index.php?category=$category&pageno=$page"
        val response = client.newCall(GET(url)).execute()
        return parseAnimeList(response.asJsoup(), true)
    }

    private fun parseAnimeList(document: Document, hasNext: Boolean): AnimesPage {
        val items = document.select("div.post-wrapper > a") 
        val animeList = items.mapNotNull { item ->
            val url = item.attr("href")
            val img = item.selectFirst("img")
            val title = img?.attr("alt") ?: item.selectFirst("div.title")?.text()

            if (url.isNotEmpty() && !title.isNullOrEmpty()) {
                SAnime.create().apply {
                    this.url = url
                    this.title = title
                    this.thumbnail_url = img?.attr("src")?.let { if (it.startsWith("http")) it else "$baseUrl/$it" }
                }
            } else null
        }
        return AnimesPage(animeList, hasNext && animeList.isNotEmpty())
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val doc = client.newCall(GET("$baseUrl/${anime.url}")).execute().asJsoup()
        val table = doc.select(".table > tbody:nth-child(1)")
        return anime.apply {
            description = table.select("tr:nth-child(12) > td:nth-child(2)").text()
            genre = table.select("tr:nth-child(5) > td:nth-child(2)").text()
            author = table.select("tr:nth-child(1)").text()
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val doc = client.newCall(GET("$baseUrl/${anime.url}")).execute().asJsoup()
        val downloadEpisode = doc.select(".btn-group > ul > li")
        
        if (downloadEpisode.isEmpty()) {
            return listOf(SEpisode.create().apply {
                name = "Movie"
                url = anime.url
                episode_number = 1F
            })
        }

        return downloadEpisode.mapIndexed { index, it ->
            val link = it.select("a").attr("href")
            val nameText = it.select("a").text()
            val span = it.select("span").text()
            SEpisode.create().apply {
                this.name = nameText.replace(span, "").trim()
                this.url = link
                this.episode_number = (index + 1).toFloat()
            }
        }.reversed()
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val doc = client.newCall(GET("$baseUrl/${episode.url}")).execute().asJsoup()
        val videoUrl = doc.select("video source").attr("src")
            .ifEmpty { doc.select("video").attr("src") }
            .ifEmpty { doc.select("a.btn").attr("href") }
        
        if (videoUrl.isNotEmpty()) {
            val absoluteVideoUrl = if (videoUrl.startsWith("http")) videoUrl else "$baseUrl/$videoUrl"
            return listOf(Video(absoluteVideoUrl, "Direct", absoluteVideoUrl))
        }
        throw IOException("Video not found")
    }

    override fun getFilterList() = AnimeFilterList(CategoryFilter())

    private class CategoryFilter : AnimeFilter.Select<String>("Category", arrayOf(
        "Latest", "Bangla Movies", "Hindi Movies", "English Movies", "Dual Audio", "South Movies", "Animated", "English Series", "Hindi Series", "Documentary"
    )) {
        private val ids = arrayOf("0", "59", "2", "19", "43", "32", "33", "36", "37", "41")
        fun toValue() = ids[state]
    }

    @Serializable data class SearchJsonItem(val id: String? = null, val image: String? = null, val name: String? = null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
