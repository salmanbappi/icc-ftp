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
        val response = chain.proceed(originalRequest)
        
        // Capture session ID from URL if present
        val urlSession = response.request.url.queryParameter("session")
        if (urlSession != null && urlSession.length > 10) {
            sessionId = urlSession
        }

        if (response.code == 302 || response.request.url.encodedPath.contains("vfw.php")) {
            response.close()
            val newResp = chain.proceed(GET(baseUrl)) // Trigger redirect chain
            val newSession = newResp.request.url.queryParameter("session")
            if (newSession != null) sessionId = newSession
            return@Interceptor newResp
        }

        response
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(sessionInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val url = if (page == 1) "$baseUrl/index.php?category=0" else "$baseUrl/index.php?category=0&pageno=$page"
        val response = client.newCall(GET(url)).execute()
        return parseAnimeList(response.asJsoup(), true)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isNotBlank()) {
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = "cSearch=$query".toRequestBody(mediaType)
            val response = client.newCall(POST("$baseUrl/command.php", body = body)).execute()
            val searchJson = try { response.parseAs<List<SearchJsonItem>>(json) } catch(e: Exception) { emptyList() }
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
        val items = document.select("div.post-wrapper > a, div.item > a, div.post-item > a") 
        val animeList = items.mapNotNull { item ->
            val url = item.attr("href")
            val img = item.selectFirst("img") ?: item.selectFirst("div.img")
            val title = img?.attr("alt") ?: item.selectFirst("div.title")?.text() ?: item.attr("title")

            if (url.isNotEmpty() && !title.isNullOrEmpty()) {
                SAnime.create().apply {
                    this.url = url
                    this.title = title
                    val imgSrc = img?.attr("src") ?: img?.attr("style")?.substringAfter("url('")?.substringBefore("')")
                    this.thumbnail_url = if (imgSrc != null) {
                        if (imgSrc.startsWith("http")) imgSrc else "$baseUrl/$imgSrc"
                    } else null
                }
            } else null
        }
        return AnimesPage(animeList, hasNext && animeList.isNotEmpty())
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val url = if (anime.url.contains("session=")) anime.url else "${anime.url}&session=$sessionId"
        val doc = client.newCall(GET("$baseUrl/$url")).execute().asJsoup()
        val table = doc.select(".table > tbody:nth-child(1)")
        return anime.apply {
            description = table.select("tr:contains(Plot) td:last-child").text().ifEmpty { table.select("tr:nth-child(12) > td:nth-child(2)").text() }
            genre = table.select("tr:contains(Genre) td:last-child").text().ifEmpty { table.select("tr:nth-child(5) > td:nth-child(2)").text() }
            author = table.select("tr:nth-child(1)").text()
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val url = if (anime.url.contains("session=")) anime.url else "${anime.url}&session=$sessionId"
        val doc = client.newCall(GET("$baseUrl/$url")).execute().asJsoup()
        val downloadEpisode = doc.select(".btn-group > ul > li")
        
        if (downloadEpisode.isEmpty()) {
            return listOf(SEpisode.create().apply {
                name = "Play Movie"
                this.url = anime.url
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
        val url = if (episode.url.startsWith("http")) episode.url else "$baseUrl/${episode.url}"
        val finalUrl = if (url.contains("session=")) url else "$url&session=$sessionId"
        val document = client.newCall(GET(finalUrl)).execute().asJsoup()
        
        val videoUrl = document.select("video source").attr("src")
            .ifEmpty { document.select("video").attr("src") }
            .ifEmpty { document.select("a.btn:contains(DOWNLOAD)").attr("href") }
            .ifEmpty { document.select("a.btn").attr("href") }
        
        if (videoUrl.isNotEmpty()) {
            val absoluteVideoUrl = if (videoUrl.startsWith("http")) videoUrl else "$baseUrl/$videoUrl"
            return listOf(Video(absoluteVideoUrl, "Direct", absoluteVideoUrl))
        }
        throw IOException("Video not found on $finalUrl")
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
