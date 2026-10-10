package com.phisher98.kineflex

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app

sealed class KineFlexStreamResult {
    data class Success(
        val url: String,
        val headers: Map<String, String>,
        val usage: KineFlexUsage?
    ) : KineFlexStreamResult()

    data class Error(
        val message: String,
        val httpCode: Int? = null
    ) : KineFlexStreamResult()
}

/**
 * Resolver for authorized KineFlex streaming sources.
 */
object KineFlexResolver {

    /**
     * Resolves playable stream for a movie via KineFlex.
     */
    suspend fun resolveMovie(tmdbId: Int, apiKey: String): KineFlexStreamResult {
        val endpoint = String.format(KineFlexConfig.KINEFLEX_MOVIE_ENDPOINT, tmdbId)
        return executeStreamRequest(endpoint, apiKey)
    }

    /**
     * Resolves playable stream for a TV episode via KineFlex.
     */
    suspend fun resolveEpisode(tmdbId: Int, season: Int, episode: Int, apiKey: String): KineFlexStreamResult {
        val endpoint = String.format(KineFlexConfig.KINEFLEX_TV_ENDPOINT, tmdbId, season, episode)
        return executeStreamRequest(endpoint, apiKey)
    }

    /**
     * Validates a KineFlex API key using the free /v1/account endpoint.
     */
    suspend fun testConnection(apiKey: String): Pair<Boolean, String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Pair(false, "API key cannot be empty")
        }

        return try {
            val response = app.get(
                KineFlexConfig.KINEFLEX_ACCOUNT_ENDPOINT,
                headers = mapOf(
                    "Authorization" to "Bearer $cleanKey",
                    "Accept" to "application/json"
                ),
                timeout = 10L
            )

            when (response.code) {
                200 -> {
                    val parsed = response.parsedSafe<KineFlexAccountResponse>()
                    if (parsed?.success == true && parsed.data != null) {
                        val balance = parsed.data.pointsBalance ?: 0
                        Pair(true, "Connected successfully! Balance: $balance points")
                    } else {
                        Pair(false, parsed?.error?.message ?: "Account verification failed")
                    }
                }
                401 -> Pair(false, "Invalid or unauthorized API key")
                403 -> Pair(false, "Access forbidden (Insufficient scope)")
                429 -> Pair(false, "Rate limit exceeded. Please wait a moment")
                else -> Pair(false, "Server returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.message}")
        }
    }

    private suspend fun executeStreamRequest(endpoint: String, apiKey: String): KineFlexStreamResult {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return KineFlexStreamResult.Error("KineFlex API key is missing. Please configure it in settings.")
        }

        return try {
            val response = app.get(
                endpoint,
                headers = mapOf(
                    "Authorization" to "Bearer $cleanKey",
                    "Accept" to "application/json"
                ),
                timeout = 25L
            )

            when (response.code) {
                200 -> {
                    val parsed = response.parsedSafe<KineFlexStreamResponse>()
                    if (parsed != null && parsed.success && !parsed.data?.url.isNullOrBlank()) {
                        val streamUrl = parsed.data!!.url!!.trim()
                        val rawHeaders = parsed.data.headers ?: emptyMap()

                        // Clean and filter valid headers
                        val sanitizedHeaders = rawHeaders
                            .filterKeys { it.isNotBlank() }
                            .filterValues { it.isNotBlank() }

                        KineFlexStreamResult.Success(
                            url = streamUrl,
                            headers = sanitizedHeaders,
                            usage = parsed.usage
                        )
                    } else {
                        val errMsg = parsed?.error?.message ?: "Playback stream not available from KineFlex"
                        KineFlexStreamResult.Error(errMsg, 200)
                    }
                }
                401 -> KineFlexStreamResult.Error("Invalid or expired KineFlex API key. Check settings.", 401)
                403 -> KineFlexStreamResult.Error("Access forbidden. Ensure your API key has stream read scope.", 403)
                404 -> KineFlexStreamResult.Error("Title or stream not found on KineFlex.", 404)
                429 -> KineFlexStreamResult.Error("KineFlex rate limit exceeded (60 req/min). Please try again shortly.", 429)
                in 500..599 -> KineFlexStreamResult.Error("KineFlex server error (HTTP ${response.code}).", response.code)
                else -> KineFlexStreamResult.Error("Unexpected response from KineFlex (HTTP ${response.code}).", response.code)
            }
        } catch (e: Exception) {
            Log.e("KineFlexResolver", "Network error resolving stream: ${e.message}")
            KineFlexStreamResult.Error("Connection failed: ${e.message}")
        }
    }
}
