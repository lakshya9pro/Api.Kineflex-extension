package com.phisher98.kineflex

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust TMDB client supporting both v3 API keys and v4 Bearer Read-Only access tokens.
 * Features in-memory caching and safe error handling.
 */
class TmdbClient(private val credentialProvider: () -> String?) {

    private val detailsCache = ConcurrentHashMap<String, TmdbDetail>()
    private val seasonCache = ConcurrentHashMap<String, List<TmdbEpisodeDetail>>()

    private fun isBearerToken(credential: String): Boolean {
        val trimmed = credential.trim()
        return trimmed.startsWith("ey", ignoreCase = true) || trimmed.length > 45
    }

    private fun buildRequestUrl(endpoint: String, credential: String): String {
        return if (isBearerToken(credential)) {
            endpoint
        } else {
            val sep = if (endpoint.contains("?")) "&" else "?"
            "$endpoint${sep}api_key=${credential.trim()}"
        }
    }

    private fun buildRequestHeaders(credential: String): Map<String, String> {
        val headers = mutableMapOf("Accept" to "application/json")
        if (isBearerToken(credential)) {
            headers["Authorization"] = "Bearer ${credential.trim()}"
        }
        return headers
    }

    /**
     * Validates TMDB credentials against the official /authentication endpoint.
     */
    suspend fun validateCredential(credential: String): Pair<Boolean, String> {
        if (credential.isBlank()) return Pair(false, "Credential cannot be empty")
        return try {
            val url = buildRequestUrl("${KineFlexConfig.TMDB_BASE_URL}/authentication", credential)
            val headers = buildRequestHeaders(credential)
            val response = app.get(url, headers = headers, timeout = 10L)

            if (response.code in 200..299) {
                Pair(true, "TMDB credential verified successfully")
            } else {
                Pair(false, "TMDB authentication failed (HTTP ${response.code})")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.message}")
        }
    }

    /**
     * Searches TMDB for movies and TV series matching the query.
     */
    suspend fun search(query: String, page: Int = 1): List<SearchResponse> {
        val credential = credentialProvider()
        if (credential.isNullOrBlank()) {
            Log.w("TmdbClient", "TMDB credential is not configured.")
            return emptyList()
        }

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val endpoint = "${KineFlexConfig.TMDB_BASE_URL}/search/multi?query=$encodedQuery&page=$page&include_adult=false"
        val url = buildRequestUrl(endpoint, credential)
        val headers = buildRequestHeaders(credential)

        return try {
            val response = app.get(url, headers = headers, timeout = 15L)
            val parsed = response.parsedSafe<TmdbSearchResults>() ?: return emptyList()

            parsed.results?.mapNotNull { item ->
                val id = item.id ?: return@mapNotNull null
                val rawType = item.mediaType?.lowercase()
                val isTv = rawType == "tv"
                val isMovie = rawType == "movie"

                // We only handle movies and TV series
                if (!isTv && !isMovie) return@mapNotNull null

                val title = (item.title ?: item.name ?: item.originalTitle ?: item.originalName)
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                val poster = item.posterPath?.let { path ->
                    "${KineFlexConfig.TMDB_IMAGE_W500}$path"
                }

                val year = (item.releaseDate ?: item.firstAirDate)?.split("-")?.firstOrNull()?.toIntOrNull()
                val navPayload = MediaIdent(id = id, type = if (isTv) "tv" else "movie").toJson()

                if (isTv) {
                    newTvSeriesSearchResponse(title, navPayload, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = year
                        this.quality = SearchQuality.HD
                    }
                } else {
                    newMovieSearchResponse(title, navPayload, TvType.Movie) {
                        this.posterUrl = poster
                        this.year = year
                        this.quality = SearchQuality.HD
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TmdbClient", "Search failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Retrieves full details for a movie or TV show.
     */
    suspend fun getDetails(id: Int, type: String): TmdbDetail? {
        val cacheKey = "${type}_$id"
        detailsCache[cacheKey]?.let { return it }

        val credential = credentialProvider()
        if (credential.isNullOrBlank()) return null

        val path = if (type == "tv") "tv" else "movie"
        val endpoint = "${KineFlexConfig.TMDB_BASE_URL}/$path/$id?append_to_response=credits,videos,recommendations"
        val url = buildRequestUrl(endpoint, credential)
        val headers = buildRequestHeaders(credential)

        return try {
            val response = app.get(url, headers = headers, timeout = 15L)
            val detail = response.parsedSafe<TmdbDetail>()
            if (detail != null) {
                detailsCache[cacheKey] = detail
            }
            detail
        } catch (e: Exception) {
            Log.e("TmdbClient", "Failed to fetch $type details for $id: ${e.message}")
            null
        }
    }

    /**
     * Retrieves episodes for a specific TV show season.
     */
    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): List<TmdbEpisodeDetail> {
        val cacheKey = "${tvId}_$seasonNumber"
        seasonCache[cacheKey]?.let { return it }

        val credential = credentialProvider()
        if (credential.isNullOrBlank()) return emptyList()

        val endpoint = "${KineFlexConfig.TMDB_BASE_URL}/tv/$tvId/season/$seasonNumber"
        val url = buildRequestUrl(endpoint, credential)
        val headers = buildRequestHeaders(credential)

        return try {
            val response = app.get(url, headers = headers, timeout = 15L)
            val seasonDetail = response.parsedSafe<TmdbSeasonDetail>()
            val episodes = seasonDetail?.episodes ?: emptyList()
            if (episodes.isNotEmpty()) {
                seasonCache[cacheKey] = episodes
            }
            episodes
        } catch (e: Exception) {
            Log.e("TmdbClient", "Failed to fetch season $seasonNumber for TV $tvId: ${e.message}")
            emptyList()
        }
    }
}
