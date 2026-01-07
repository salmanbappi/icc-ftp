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
        
        val urlSession = response.request.url.queryParameter("session")
        if (urlSession != null && urlSession.length > 10) {
            sessionId = urlSession
        }

        if (response.code == 302 || response.request.url.encodedPath.contains("vfw.php")) {
            response.close()
            val newResp = chain.proceed(GET(baseUrl))
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

    // Popular = Top Slider items
    override suspend fun getPopularAnime(page: Int): AnimesPage {
        if (page > 1) return AnimesPage(emptyList(), false)
        val response = client.newCall(GET("$baseUrl/dashboard.php?category=0")).execute()
        val doc = response.asJsoup()
        val items = doc.select("div#post-slider-multipost div.item")
        val animeList = items.mapNotNull { item ->
            val link = item.parent() ?: return@mapNotNull null
            val url = link.attr("href")
            val imgStyle = item.selectFirst("div.img")?.attr("style") ?: ""
            val imgSrc = imgStyle.substringAfter("url('").substringBefore("')")
            val title = item.selectFirst("div.title span")?.text()

            if (url.isNotEmpty() && !title.isNullOrEmpty()) {
                SAnime.create().apply {
                    this.url = url
                    this.title = title
                    this.thumbnail_url = if (imgSrc.isNotEmpty()) "$baseUrl/$imgSrc" else null
                }
            } else null
        }
        return AnimesPage(animeList, false)
    }

    // Latest = Grid items with working infinite scroll
    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val request = if (page == 1) {
            GET("$baseUrl/dashboard.php?category=0")
        } else {
            val body = FormBody.Builder().add("cpage", page.toString()).build()
            POST("$baseUrl/command.php", body = body)
        }
        val response = client.newCall(request).execute()
        return parseAnimeList(response.asJsoup(), true)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isNotBlank()) {
            if (page > 1) return AnimesPage(emptyList(), false)
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
        filters.forEach { filter ->
            when (filter) {
                is CategoryFilter -> if(filter.toValue() != "0") category = filter.toValue()
                is TVShowFilter -> if(filter.toValue() != "0") category = filter.toValue()
                is OtherFilter -> if(filter.toValue() != "0") category = filter.toValue()
                else -> {}
            }
        }
        
        // Use dashboard.php for filtering as it was reported working previously
        val url = "$baseUrl/dashboard.php?category=$category"
        val response = client.newCall(GET(url)).execute()
        return parseAnimeList(response.asJsoup(), false) // Disable pageno for filters to prevent repetition
    }

    private fun parseAnimeList(document: Document, hasNext: Boolean): AnimesPage {
        // Use broad selectors to catch items in AJAX fragments and full pages
        val items = document.select("div.post, div.post-wrapper > a, div.item > a, div.post-item > a") 
        val animeList = items.mapNotNull { item ->
            val url = if (item.tagName() == "a") item.attr("href") else item.selectFirst("a")?.attr("href") ?: ""
            val img = item.selectFirst("img") ?: item.selectFirst("div.img")
            val title = img?.attr("alt") ?: item.selectFirst("div.title")?.text() ?: item.attr("title") ?: item.selectFirst("span")?.text()

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
            description = table.select("tr:contains(Plot) td:last-child").text()
            genre = table.select("tr:contains(Genre) td:last-child").text()
            author = table.select("tr:nth-child(1)").text()
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val url = if (anime.url.contains("session=")) anime.url else "${anime.url}&session=$sessionId"
        val doc = client.newCall(GET("$baseUrl/$url")).execute().asJsoup()
        val downloadEpisode = doc.select(".btn-group > ul > li")
        
        if (downloadEpisode.isEmpty()) {
            val videoLink = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("a.btn:contains(DOWNLOAD)")?.attr("href")
                ?: doc.selectFirst("a.btn")?.attr("href") ?: ""
            
            return listOf(SEpisode.create().apply {
                name = "Play Movie"
                this.url = videoLink
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
        if (episode.url.isBlank()) throw IOException("Video URL is empty")
        val videoUrl = if (episode.url.startsWith("http")) episode.url else "$baseUrl/${episode.url}"
        return listOf(Video(videoUrl, "Direct", videoUrl))
    }

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("Use only one filter at a time"),
        CategoryFilter(),
        TVShowFilter(),
        OtherFilter()
    )

    private open class SelectFilter(name: String, val items: Array<Pair<String, String>>) : AnimeFilter.Select<String>(name, items.map { it.first }.toTypedArray()) {
        fun toValue() = items[state].second
    }

    private class CategoryFilter : SelectFilter("Movies", arrayOf(
        "None" to "0", "3D" to "9", "4K" to "74", "Animated" to "33", "Anime" to "83", "Bangla (BD)" to "59", 
        "Bangla (Kolkata)" to "60", "Chinese" to "76", "Documentaries" to "41", "Dual Audio" to "43", 
        "English" to "19", "Exclusive Full-HD" to "44", "Hindi" to "2", "Indonesian" to "79", 
        "Japanese" to "80", "Korean" to "75", "Other Foreign" to "64", "Pakistani" to "77", 
        "Punjabi" to "71", "South Indian (Hindi Dub)" to "32", "South Indian" to "73"
    ))

    private class TVShowFilter : SelectFilter("TV Shows", arrayOf(
        "None" to "0", "Awards" to "38", "Bangla Drama" to "34", "Bangla Telefilm" to "35", 
        "Serials (Animation)" to "82", "Serials (Anime)" to "78", "Serials (Bangla)" to "39", 
        "Serials (Documentaries)" to "81", "Serials (Dual Audio)" to "72", "Serials (English)" to "36", 
        "Serials (Hindi)" to "37", "Serials (Others)" to "70", "WWE" to "52"
    ))

    private class OtherFilter : SelectFilter("Others", arrayOf(
        "None" to "0", "Kids (Cartoon)" to "66", "Learning" to "53"
    ))

    @Serializable data class SearchJsonItem(val id: String? = null, val image: String? = null, val name: String? = null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}