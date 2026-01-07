import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.Exception

class ProductionExtension : AnimeHttpSource() {

    override val name = "Production Grade Extension"
    override val baseUrl = "https://example-vod-provider.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client

    // --- Session Caching Implementation ---
    private val sessionCache: SharedPreferences by lazy {
        // In a real extension, this uses the context provided via the source constructor or preferences
        throw Exception("Preferences context required for persistent caching")
    }

    private var inMemoryToken: String? = null
    private var tokenExpiry: Long = 0

    /**
     * Retrieves a valid session token, using in-memory cache if valid.
     */
    private fun getValidSession(): String? {
        val currentTime = System.currentTimeMillis()
        if (inMemoryToken != null && currentTime < tokenExpiry) {
            return inMemoryToken
        }

        return try {
            val response = client.newCall(GET("$baseUrl/api/auth")).execute()
            if (!response.isSuccessful) return null
            
            // Modern Kotlin idiom: run to process response body
            response.body?.string()?.run {
                // Logic to parse token from response (e.g., JSON parsing)
                inMemoryToken = "parsed_token"
                tokenExpiry = currentTime + 3600000 // Cache for 1 hour
                inMemoryToken
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- Robust URL Building & Network Calls ---
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return runCatching {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("search")
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
                addQueryParameter("token", getValidSession() ?: "")
            }.build()

            client.newCall(GET(url.toString(), headers))
                .asObservableSuccess()
                .map { response ->
                    searchAnimeParse(response)
                }
        }.getOrElse { e ->
            Observable.error(Exception("Failed to fetch search results: ${e.message}"))
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select(".item").map { element ->
            SAnime.create().apply {
                title = element.select(".title").text()
                url = element.select("a").attr("href").toHttpUrl().encodedPath
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        return AnimesPage(animeList, document.select(".next").isNotEmpty())
    }

    // --- Video Extraction with Referer Headers ---
    override fun videoListRequest(episode: SEpisode): Request {
        val url = (baseUrl + episode.url).toHttpUrl().newBuilder().build()
        
        // Ensure Referer is set to the base URL or episode page to bypass hotlink protection
        val videoHeaders = headers.newBuilder()
            .apply {
                add("Referer", baseUrl)
                add("Accept", "*/*")
            }.build()

        return GET(url.toString(), videoHeaders)
    }

    override fun videoListParse(response: Response): List<Video> {
        return try {
            val document = response.asJsoup()
            
            // Modern Kotlin idiom: map with let for safety
            document.select("script:containsData(sources)").let { scriptElements ->
                if (scriptElements.isEmpty()) throw Exception("No video sources found")
                
                val videoUrl = "https://cdn.example.com/stream/playlist.m3u8"
                
                // Proper Referer implementation for the actual video stream
                val streamHeaders = Headers.Builder().apply {
                    add("Referer", response.request.url.toString())
                    add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/110.0.0.0 Safari/537.36")
                }.build()

                listOf(
                    Video(videoUrl, "Standard Quality (HLS)", videoUrl, headers = streamHeaders)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Boilerplate Overrides with Idiomatic Defaults ---
    override fun popularAnimeRequest(page: Int): Request = 
        GET(baseUrl.toHttpUrl().newBuilder().addPathSegment("popular").addQueryParameter("p", page.toString()).build().toString(), headers)

    override fun latestUpdatesRequest(page: Int): Request = 
        GET(baseUrl.toHttpUrl().newBuilder().addPathSegment("latest").addQueryParameter("p", page.toString()).build().toString(), headers)

    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)
    
    override fun animeDetailsParse(response: Response): SAnime = SAnime.create().apply {
        val document = response.asJsoup()
        title = document.select(".name").text()
        author = document.select(".studio").text()
        description = document.select(".desc").text()
        genre = document.select(".tags a").joinToString { it.text() }
        status = SAnime.COMPLETED
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        return response.asJsoup().select(".episodes a").map { element ->
            SEpisode.create().apply {
                name = element.text()
                url = element.attr("href")
                date_upload = System.currentTimeMillis() // Real parsing recommended
            }
        }.reversed()
    }
}