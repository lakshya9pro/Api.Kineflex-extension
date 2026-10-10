package com.phisher98.kineflex

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson

/**
 * Parser for KineFlex category JSON feeds.
 * Safely handles both array feeds (like Latest Now) and wrapped object feeds.
 */
object KineFlexFeedParser {
    private val mapper = jacksonObjectMapper()

    /**
     * Parses raw JSON string into a list of FeedItem.
     */
    fun parseFeedItems(json: String): List<FeedItem> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                mapper.readValue<List<FeedItem>>(trimmed)
            } else if (trimmed.startsWith("{")) {
                val wrapper = mapper.readValue<FeedObjectWrapper>(trimmed)
                wrapper.results ?: wrapper.items ?: wrapper.data ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("KineFlexFeedParser", "Failed to parse feed JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Converts a feed item into a CloudStream SearchResponse.
     * Rejects invalid items without fabricated data.
     */
    fun FeedItem.toSearchResponse(): SearchResponse? {
        val id = resolvedId ?: return null
        val title = resolvedTitle ?: return null
        val poster = resolvedPosterUrl
        val year = resolvedYear
        val type = resolvedType

        val navUrl = MediaIdent(id = id, type = type).toJson()

        return if (type == "tv") {
            newTvSeriesSearchResponse(title, navUrl, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
                this.quality = SearchQuality.HD
            }
        } else {
            newMovieSearchResponse(title, navUrl, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
                this.quality = SearchQuality.HD
            }
        }
    }

    /**
     * Converts raw JSON feed into de-duplicated CloudStream SearchResponses.
     */
    fun parseToSearchResponses(json: String): List<SearchResponse> {
        val items = parseFeedItems(json)
        return items
            .filter { it.resolvedId != null && !it.resolvedTitle.isNullOrBlank() }
            .distinctBy { "${it.resolvedType}_${it.resolvedId}" }
            .mapNotNull { it.toSearchResponse() }
    }
}
