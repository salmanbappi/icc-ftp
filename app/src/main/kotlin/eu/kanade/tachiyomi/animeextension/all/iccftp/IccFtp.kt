package eu.kanade.tachiyomi.animeextension.all.iccftp

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
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

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var sessionId: String by preferences.delegate("session_id", "")

    private val sessionInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Skip recursion
        if (originalUrl.encodedPath == "/" && originalUrl.querySize == 0) {
            return@Interceptor chain.proceed(originalRequest)
        }

        val newUrl = if (originalUrl.queryParameter("session") == null) {
            if (sessionId.isBlank()) {
                fetchSessionId()
            }
            originalUrl.newBuilder()
                .addQueryParameter("session", sessionId)
                .build()
        } else {
            originalUrl
        }

        val request = originalRequest.newBuilder().url(newUrl).build()
        val response = chain.proceed(request)

        // If redirected to login or dashboard without session, invalidate and retry
        if (response.request.url.encodedPath.contains("vfw.php") || (response.code == 302 && response.header("Location")?.contains("vfw.php") == true)) {
            response.close()
            sessionId = "" // Invalidate
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
            // This request will trigger the redirect chain
            // http://10.16.100.244/ -> ncisvfwall -> dashboard.php?session=...
            val initialReq = GET(baseUrl)
            val response = client.newCall(initialReq).execute()
            val finalUrl = response.request.url
            response.close()

            val session = finalUrl.queryParameter("session")
            if (session != null) {
                sessionId = session
            } else {
                throw IOException("Failed to extract session ID from ${finalUrl}")
            }
        } catch (e: Exception) {
            throw IOException("Failed to obtain session ID: ${e.message}")
        }
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val response = client.newCall(GET("$baseUrl/dashboard.php")).execute()
        val document = response.asJsoup()
        return parseAnimeList(document)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        // We need the token from the dashboard first
        val dashboard = client.newCall(GET("$baseUrl/dashboard.php")).execute().asJsoup()
        val token = dashboard.selectFirst("input[name=token]")?.attr("value") 
            ?: throw IOException("CSRF Token not found")

        val body = FormBody.Builder()
            .add("token", token)
            .add("psearch", query)
            .build()

        val response = client.newCall(POST("$baseUrl/dashboard.php", body = body)).execute()
        return parseAnimeList(response.asJsoup())
    }

    private fun parseAnimeList(document: Document): AnimesPage {
        val items = document.select("div.item, div.post-item") // Trying to match potential grid items
        val animeList = items.mapNotNull { item ->
            val link = item.parent() // The <a> tag wraps the div.item in the dump
            val url = link.attr("href")
            val img = item.selectFirst("div.img")?.attr("style")
                ?.substringAfter("url('")?.substringBefore("')")
            val title = item.selectFirst("div.title span")?.text()

            if (url.isNotEmpty() && title != null) {
                SAnime.create().apply {
                    this.url = url // Contains player.php?session=...&play=ID
                    this.title = title
                    this.thumbnail_url = if (img != null) "$baseUrl/$img" else null
                }
            } else {
                null
            }
        }
        return AnimesPage(animeList, false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return anime.apply {
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                name = "Movie"
                url = anime.url
                date_upload = 0L
                episode_number = 1F
            }
        )
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val document = client.newCall(GET("$baseUrl/${episode.url}")).execute().asJsoup()
        
        // Strategy: Look for video tag or source
        val videoUrl = document.select("video source").attr("src")
            .ifEmpty { document.select("video").attr("src") }
        
        if (videoUrl.isNotEmpty()) {
            return listOf(Video(videoUrl, "Quality", videoUrl))
        }
        
        throw IOException("Video URL not found")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
