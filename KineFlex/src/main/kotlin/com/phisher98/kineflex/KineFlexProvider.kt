package com.phisher98.kineflex

import android.content.SharedPreferences
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * CloudStream MainAPI implementation for KineFlex.
 * Features 4 custom JSON home feeds, TMDB search and metadata, and authorized KineFlex playback.
 */
class KineFlexProvider(
    private val plugin: KineFlexPlugin,
    private val sharedPref: SharedPreferences
) : MainAPI() {

    override var mainUrl = KineFlexConfig.KINEFLEX_BASE_URL
    override var name = "KineFlex"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    val tmdbClient = TmdbClient {
        KineFlexConfig.getTmdbCredential(sharedPref)
    }

    // Exactly four official home categories
    override val mainPage = mainPageOf(
        "latest_now" to "Latest Now",
        "trending_now" to "Trending Now",
        "premium" to "Premium",
        "other" to "Other"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val categoryId = request.data
        val feedUrl = KineFlexConfig.getCategoryUrl(categoryId, sharedPref)

        if (feedUrl.isBlank()) {
            Log.d("KineFlex", "Feed URL for category '$categoryId' is not configured.")
            return newHomePageResponse(request.name, emptyList(), hasNext = false)
        }

        return try {
            val response = app.get(feedUrl, timeout = 15L)
            if (response.code !in 200..299) {
                Log.w("KineFlex", "Failed to fetch feed '$categoryId' (HTTP ${response.code})")
                return newHomePageResponse(request.name, emptyList(), hasNext = false)
            }

            val items = KineFlexFeedParser.parseToSearchResponses(response.text)
            newHomePageResponse(request.name, items, hasNext = false)
        } catch (e: Exception) {
            Log.e("KineFlex", "Error loading main page category '$categoryId': ${e.message}")
            newHomePageResponse(request.name, emptyList(), hasNext = false)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val credential = KineFlexConfig.getTmdbCredential(sharedPref)
        if (credential.isNullOrBlank()) {
            showToast("Configure your TMDB credential in KineFlex settings to enable search.")
            return emptyList()
        }
        return tmdbClient.search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val ident = tryParseJson<MediaIdent>(url)
            ?: parseMediaIdentFallback(url)
            ?: return null

        val isTv = ident.type == "tv"
        val tmdbDetail = tmdbClient.getDetails(ident.id, ident.type)

        return if (tmdbDetail != null) {
            buildLoadResponseFromTmdb(ident, tmdbDetail, url, isTv)
        } else {
            // Fallback load response if TMDB is temporarily unavailable or unconfigured
            buildFallbackLoadResponse(ident, url, isTv)
        }
    }

    private suspend fun buildLoadResponseFromTmdb(
        ident: MediaIdent,
        res: TmdbDetail,
        url: String,
        isTv: Boolean
    ): LoadResponse {
        val title = (res.title ?: res.name ?: res.originalTitle ?: res.originalName) ?: "Title #${ident.id}"
        val poster = res.posterPath?.let { "${KineFlexConfig.TMDB_IMAGE_ORIGINAL}$it" }
        val backdrop = res.backdropPath?.let { "${KineFlexConfig.TMDB_IMAGE_ORIGINAL}$it" }
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val rating = res.voteAverage?.times(1000)?.roundToInt()
        val genres = res.genres?.mapNotNull { it.name }
        val trailerUrl = res.videos?.results?.firstOrNull {
            it.site.equals("YouTube", ignoreCase = true) && !it.key.isNullOrBlank()
        }?.let { "https://www.youtube.com/watch?v=${it.key}" }

        val actors = res.credits?.cast?.take(15)?.mapNotNull { cast ->
            val castName = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                Actor(
                    castName,
                    cast.profilePath?.let { "${KineFlexConfig.TMDB_IMAGE_W500}$it" }
                ),
                roleString = cast.character
            )
        }

        val recommendations = res.recommendations?.results?.mapNotNull { rec ->
            val recId = rec.id ?: return@mapNotNull null
            val recTitle = rec.title ?: rec.name ?: return@mapNotNull null
            val recType = rec.mediaType?.lowercase() ?: if (isTv) "tv" else "movie"
            val recPoster = rec.posterPath?.let { "${KineFlexConfig.TMDB_IMAGE_W500}$it" }
            val recPayload = MediaIdent(id = recId, type = recType).toJson()

            if (recType == "tv") {
                newTvSeriesSearchResponse(recTitle, recPayload, TvType.TvSeries) {
                    this.posterUrl = recPoster
                }
            } else {
                newMovieSearchResponse(recTitle, recPayload, TvType.Movie) {
                    this.posterUrl = recPoster
                }
            }
        }

        if (isTv) {
            val episodes = coroutineScope {
                val seasons = res.seasons?.filter { (it.seasonNumber ?: -1) > 0 } ?: emptyList()
                seasons.map { seasonSummary ->
                    async {
                        val seasonNum = seasonSummary.seasonNumber ?: 1
                        val seasonEpisodes = tmdbClient.getSeasonEpisodes(ident.id, seasonNum)

                        seasonEpisodes.map { ep ->
                            val epNumber = ep.episodeNumber ?: 1
                            val epData = KineFlexPlaybackData(
                                tmdbId = ident.id,
                                type = "tv",
                                season = seasonNum,
                                episode = epNumber,
                                title = ep.name
                            ).toJson()

                            newEpisode(epData) {
                                this.name = ep.name ?: "Episode $epNumber"
                                this.season = seasonNum
                                this.episode = epNumber
                                this.posterUrl = ep.stillPath?.let { "${KineFlexConfig.TMDB_IMAGE_W500}$it" }
                                this.rating = ep.voteAverage?.times(1000)?.roundToInt()
                                this.description = ep.overview
                                this.addDate(ep.airDate)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = res.overview
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                if (!actors.isNullOrEmpty()) addActors(actors)
                addTrailer(trailerUrl)
                addTMDbId(ident.id.toString())
                addImdbId(res.imdbId)
            }
        } else {
            val movieData = KineFlexPlaybackData(
                tmdbId = ident.id,
                type = "movie",
                title = title
            ).toJson()

            return newMovieLoadResponse(title, url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                if (!actors.isNullOrEmpty()) addActors(actors)
                addTrailer(trailerUrl)
                addTMDbId(ident.id.toString())
                addImdbId(res.imdbId)
            }
        }
    }

    private fun buildFallbackLoadResponse(ident: MediaIdent, url: String, isTv: Boolean): LoadResponse {
        val title = "${if (isTv) "TV Series" else "Movie"} #${ident.id}"
        showToast("Loading without full TMDB metadata. Set TMDB credential in settings.")

        return if (isTv) {
            val defaultEp = newEpisode(
                KineFlexPlaybackData(tmdbId = ident.id, type = "tv", season = 1, episode = 1, title = "Episode 1").toJson()
            ) {
                this.season = 1
                this.episode = 1
                this.name = "Episode 1"
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(defaultEp)) {
                addTMDbId(ident.id.toString())
            }
        } else {
            val movieData = KineFlexPlaybackData(tmdbId = ident.id, type = "movie", title = title).toJson()
            newMovieLoadResponse(title, url, TvType.Movie, movieData) {
                addTMDbId(ident.id.toString())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playbackData = tryParseJson<KineFlexPlaybackData>(data)
        if (playbackData == null) {
            Log.e("KineFlex", "Invalid playback data payload: $data")
            showToast("Invalid playback payload")
            return false
        }

        val apiKey = KineFlexConfig.getKineFlexApiKey(sharedPref)
        if (apiKey.isNullOrBlank()) {
            showToast("KineFlex API key is missing. Open settings to configure your key.")
            return false
        }

        val streamResult = if (playbackData.type == "tv") {
            KineFlexResolver.resolveEpisode(
                tmdbId = playbackData.tmdbId,
                season = playbackData.season ?: 1,
                episode = playbackData.episode ?: 1,
                apiKey = apiKey
            )
        } else {
            KineFlexResolver.resolveMovie(
                tmdbId = playbackData.tmdbId,
                apiKey = apiKey
            )
        }

        return when (streamResult) {
            is KineFlexStreamResult.Success -> {
                val streamUrl = streamResult.url
                val headers = streamResult.headers

                if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                    var m3u8Found = false
                    try {
                        M3u8Helper.generateM3u8(
                            source = name,
                            streamUrl = streamUrl,
                            referer = headers["Referer"] ?: "",
                            headers = headers
                        ).forEach { link ->
                            m3u8Found = true
                            callback(link)
                        }
                    } catch (e: Exception) {
                        Log.w("KineFlex", "M3u8Helper failed to parse playlist: ${e.message}")
                    }

                    if (!m3u8Found) {
                        callback(
                            newExtractorLink(
                                name = "$name HLS",
                                source = name,
                                url = streamUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.headers = headers
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                } else {
                    callback(
                        newExtractorLink(
                            name = "$name Stream",
                            source = name,
                            url = streamUrl,
                            type = INFER_TYPE
                        ) {
                            this.headers = headers
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
                true
            }

            is KineFlexStreamResult.Error -> {
                showToast(streamResult.message)
                false
            }
        }
    }

    private fun parseMediaIdentFallback(url: String): MediaIdent? {
        val trimmed = url.trim()
        val parts = trimmed.split("/")
        if (parts.size >= 2) {
            val type = parts[0].lowercase()
            val id = parts[1].toIntOrNull()
            if (id != null && (type == "movie" || type == "tv")) {
                return MediaIdent(id = id, type = type)
            }
        }
        val idOnly = trimmed.toIntOrNull()
        if (idOnly != null) {
            return MediaIdent(id = idOnly, type = "movie")
        }
        return null
    }
}
