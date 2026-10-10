package com.phisher98.kineflex

import android.content.SharedPreferences
import androidx.core.content.edit
import com.phisher98.BuildConfig

/**
 * Centralized configuration for the KineFlex CloudStream extension.
 * Manages category feed URLs, API endpoints, and preference storage.
 */
object KineFlexConfig {
    const val PREFS_NAME = "KineFlexPreferences"

    // Preference storage keys
    const val KEY_KINEFLEX_API_KEY = "kineflex_api_key"
    const val KEY_TMDB_API_KEY = "tmdb_api_key"

    // Category URL keys
    const val KEY_URL_LATEST_NOW = "url_latest_now"
    const val KEY_URL_TRENDING_NOW = "url_trending_now"
    const val KEY_URL_PREMIUM = "url_premium"
    const val KEY_URL_OTHER = "url_other"

    // Default category feed URLs
    const val DEFAULT_LATEST_NOW_URL = "https://www.jsonkeeper.com/b/IHDLH"
    const val DEFAULT_TRENDING_NOW_URL = ""
    const val DEFAULT_PREMIUM_URL = ""
    const val DEFAULT_OTHER_URL = ""

    // KineFlex API Endpoints
    const val KINEFLEX_BASE_URL = "https://api.kineflex.site"
    const val KINEFLEX_MOVIE_ENDPOINT = "$KINEFLEX_BASE_URL/v1/movie/%d"
    const val KINEFLEX_TV_ENDPOINT = "$KINEFLEX_BASE_URL/v1/tv/%d/%d/%d"
    const val KINEFLEX_ACCOUNT_ENDPOINT = "$KINEFLEX_BASE_URL/v1/account"
    const val KINEFLEX_PREMIUM_CATALOG_ENDPOINT = "$KINEFLEX_BASE_URL/v1/premium"
    const val KINEFLEX_HEALTH_ENDPOINT = "$KINEFLEX_BASE_URL/health"

    // TMDB API Endpoints
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_ORIGINAL = "https://image.tmdb.org/t/p/original"

    data class FeedCategory(
        val id: String,
        val title: String,
        val prefKey: String,
        val defaultUrl: String,
        val description: String
    )

    // The exactly four official home categories
    val CATEGORIES = listOf(
        FeedCategory(
            id = "latest_now",
            title = "Latest Now",
            prefKey = KEY_URL_LATEST_NOW,
            defaultUrl = DEFAULT_LATEST_NOW_URL,
            description = "Recently released movies and TV series"
        ),
        FeedCategory(
            id = "trending_now",
            title = "Trending Now",
            prefKey = KEY_URL_TRENDING_NOW,
            defaultUrl = DEFAULT_TRENDING_NOW_URL,
            description = "Trending content feed"
        ),
        FeedCategory(
            id = "premium",
            title = "Premium",
            prefKey = KEY_URL_PREMIUM,
            defaultUrl = DEFAULT_PREMIUM_URL,
            description = "Curated premium titles"
        ),
        FeedCategory(
            id = "other",
            title = "Other",
            prefKey = KEY_URL_OTHER,
            defaultUrl = DEFAULT_OTHER_URL,
            description = "Additional catalog feeds"
        )
    )

    /**
     * Resolves the configured JSON URL for a given category.
     * Checks user preferences first, then falls back to defaultUrl.
     */
    fun getCategoryUrl(categoryId: String, prefs: SharedPreferences?): String {
        val category = CATEGORIES.firstOrNull { it.id == categoryId } ?: return ""
        val savedUrl = prefs?.getString(category.prefKey, null)?.trim()
        return if (!savedUrl.isNullOrEmpty()) savedUrl else category.defaultUrl
    }

    /**
     * Saves a custom JSON URL for a category.
     */
    fun setCategoryUrl(categoryId: String, url: String, prefs: SharedPreferences) {
        val category = CATEGORIES.firstOrNull { it.id == categoryId } ?: return
        prefs.edit {
            putString(category.prefKey, url.trim())
        }
    }

    /**
     * Resets category URLs back to defaults.
     */
    fun resetCategoryUrls(prefs: SharedPreferences) {
        prefs.edit {
            CATEGORIES.forEach { remove(it.prefKey) }
        }
    }

    /**
     * Retrieves the configured KineFlex API key.
     */
    fun getKineFlexApiKey(prefs: SharedPreferences?): String? {
        val key = prefs?.getString(KEY_KINEFLEX_API_KEY, null)?.trim()
        return if (key.isNullOrEmpty()) null else key
    }

    /**
     * Saves the KineFlex API key.
     */
    fun setKineFlexApiKey(key: String, prefs: SharedPreferences) {
        prefs.edit {
            putString(KEY_KINEFLEX_API_KEY, key.trim())
        }
    }

    /**
     * Clears the KineFlex API key.
     */
    fun clearKineFlexApiKey(prefs: SharedPreferences) {
        prefs.edit {
            remove(KEY_KINEFLEX_API_KEY)
        }
    }

    /**
     * Retrieves the configured TMDB credential (API Key or Read-Only Access Token).
     * Falls back to BuildConfig.TMDB_API if present.
     */
    fun getTmdbCredential(prefs: SharedPreferences?): String? {
        val customKey = prefs?.getString(KEY_TMDB_API_KEY, null)?.trim()
        if (!customKey.isNullOrEmpty()) return customKey

        val buildKey = try {
            BuildConfig.TMDB_API.trim()
        } catch (_: Throwable) {
            ""
        }
        return if (buildKey.isNotEmpty()) buildKey else null
    }

    /**
     * Saves the TMDB credential.
     */
    fun setTmdbCredential(key: String, prefs: SharedPreferences) {
        prefs.edit {
            putString(KEY_TMDB_API_KEY, key.trim())
        }
    }

    /**
     * Clears the TMDB credential.
     */
    fun clearTmdbCredential(prefs: SharedPreferences) {
        prefs.edit {
            remove(KEY_TMDB_API_KEY)
        }
    }

    /**
     * Safely masks an API key for UI display (e.g. kine_live_••••••3a4b).
     * Never reveals the full secret.
     */
    fun maskSecret(secret: String?): String {
        if (secret.isNullOrBlank()) return "Not configured"
        val trimmed = secret.trim()
        return if (trimmed.length <= 8) {
            "••••••••"
        } else {
            val prefix = trimmed.take(4)
            val suffix = trimmed.takeLast(4)
            "$prefix••••••••$suffix"
        }
    }
}
