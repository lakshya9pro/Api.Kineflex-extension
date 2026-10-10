package com.phisher98.kineflex

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data models for KineFlex home JSON feeds, navigation, TMDB metadata, and KineFlex API responses.
 */

// ==========================================
// 1. Home Feed Models
// ==========================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeedItem(
    @JsonProperty("id") val id: Any? = null,
    @JsonProperty("tmdb_id") val tmdbId: Any? = null,
    @JsonProperty("tmdbId") val tmdbIdCamel: Any? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("year") val year: Any? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("imgs") val imgs: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null
) {
    val resolvedId: Int?
        get() = (id ?: tmdbId ?: tmdbIdCamel)?.toString()?.trim()?.toIntOrNull()

    val resolvedTitle: String?
        get() = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }

    val resolvedYear: Int?
        get() = year?.toString()?.trim()?.toIntOrNull()
            ?: releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
            ?: firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()

    val resolvedPosterUrl: String?
        get() {
            val candidate = imgs ?: image ?: poster ?: posterUrl
            if (!candidate.isNullOrBlank()) {
                return if (candidate.startsWith("/")) {
                    "${KineFlexConfig.TMDB_IMAGE_W500}$candidate"
                } else {
                    candidate
                }
            }
            if (!posterPath.isNullOrBlank()) {
                val path = if (posterPath.startsWith("/")) posterPath else "/$posterPath"
                return "${KineFlexConfig.TMDB_IMAGE_W500}$path"
            }
            return null
        }

    val resolvedType: String
        get() {
            val raw = (type ?: mediaType)?.lowercase()?.trim()
            return when (raw) {
                "tv", "series", "show", "tvseries" -> "tv"
                else -> "movie"
            }
        }
}

// Wrapper for feeds formatted as an object { "results": [...] } or { "items": [...] }
@JsonIgnoreProperties(ignoreUnknown = true)
data class FeedObjectWrapper(
    @JsonProperty("results") val results: List<FeedItem>? = null,
    @JsonProperty("items") val items: List<FeedItem>? = null,
    @JsonProperty("data") val data: List<FeedItem>? = null
)

// ==========================================
// 2. Navigation & Playback Payloads
// ==========================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaIdent(
    @JsonProperty("id") val id: Int,
    @JsonProperty("type") val type: String // "movie" or "tv"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexPlaybackData(
    @JsonProperty("tmdbId") val tmdbId: Int,
    @JsonProperty("type") val type: String, // "movie" or "tv"
    @JsonProperty("season") val season: Int? = null,
    @JsonProperty("episode") val episode: Int? = null,
    @JsonProperty("title") val title: String? = null
)

// ==========================================
// 3. KineFlex API Models
// ==========================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexStreamResponse(
    @JsonProperty("success") val success: Boolean = false,
    @JsonProperty("data") val data: KineFlexStreamData? = null,
    @JsonProperty("error") val error: KineFlexError? = null,
    @JsonProperty("usage") val usage: KineFlexUsage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexStreamData(
    @JsonProperty("id") val id: Any? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexError(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("message") val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexUsage(
    @JsonProperty("cost") val cost: Int? = null,
    @JsonProperty("remaining_points") val remainingPoints: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexAccountResponse(
    @JsonProperty("success") val success: Boolean = false,
    @JsonProperty("data") val data: KineFlexAccountData? = null,
    @JsonProperty("error") val error: KineFlexError? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexAccountData(
    @JsonProperty("points_balance") val pointsBalance: Int? = null,
    @JsonProperty("costs") val costs: Map<String, Int>? = null,
    @JsonProperty("key") val key: KineFlexKeyInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KineFlexKeyInfo(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("prefix") val prefix: String? = null,
    @JsonProperty("scopes") val scopes: List<String>? = null
)

// ==========================================
// 4. TMDB API Models
// ==========================================

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSearchResults(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: List<TmdbMediaItem>? = null,
    @JsonProperty("total_pages") val totalPages: Int? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbMediaItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("overview") val overview: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("original_language") val originalLanguage: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("recommendations") val recommendations: TmdbSearchResults? = null,
    @JsonProperty("seasons") val seasons: List<TmdbSeasonSummary>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCast>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCast(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideos(
    @JsonProperty("results") val results: List<TmdbVideo>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideo(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeasonSummary(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episode_count") val episodeCount: Int? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeasonDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episodes") val episodes: List<TmdbEpisodeDetail>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbEpisodeDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbAuthResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("status_code") val statusCode: Int? = null,
    @JsonProperty("status_message") val statusMessage: String? = null
)
