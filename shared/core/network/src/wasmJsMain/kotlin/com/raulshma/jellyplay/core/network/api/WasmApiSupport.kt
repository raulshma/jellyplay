package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.auth.JellyfinAuthorizationHeader
import com.raulshma.jellyplay.core.network.wasmWireJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * The client identity the SDK puts on the wire (Authorization header builder
 * inputs + the device id sessions are keyed by). Shared by every wasm API
 * client so all requests authenticate identically and
 * `fetchActiveTranscodeReasons` can match this device's session.
 */
data class WasmClientIdentity(
    val clientName: String,
    val clientVersion: String,
    val deviceId: String,
    val deviceName: String,
)

/**
 * Phase W chunk 2: the Ktor request plumbing chunk 1 built inside
 * [KtorWasmAuthApiClient], factored into a base class so the auth /
 * library / playback wasm clients share ONE implementation of the wire
 * mechanics: URL joining, the SDK-identical `Authorization` header, JSON
 * GET/POST/DELETE helpers, byte fetches, error mapping onto [ApiException]
 * and the `apiResult`/`apiResultWithRetry` pair (engine semantics, see the
 * KDoc there — same recoverCatching/cancellation parity note).
 *
 * Session discipline: base URL + token are derived PER REQUEST from the
 * shared [AtomicSessionState] the auth client publishes into (the wasm
 * replacement for `JellyfinApiEngine.requireApi()`). Per CONTEXT.md's atomic
 * session rule, "is there a session" questions read the atomic
 * [AtomicSessionState.session] value — never a combine of the two flows.
 */
open class WasmApiSupport(
    protected val httpClient: HttpClient,
    protected val sessionState: AtomicSessionState,
    private val identity: WasmClientIdentity,
) {
    /** SDK-lenient wire decoding — the shared wasm instance (see [wasmWireJson]). */
    protected val wireJson: Json = wasmWireJson

    /** `IllegalStateException("Not connected to server")`, the engine's requireApi() contract. */
    protected fun requireConnectedServer(): ServerInfo =
        sessionState.currentServer.value ?: throw IllegalStateException("Not connected to server")

    /** `IllegalStateException("Not authenticated")`, the jvmShared impls' user contract. */
    protected fun requireCurrentUser(): UserInfo =
        sessionState.currentUser.value ?: throw IllegalStateException("Not authenticated")

    /**
     * Per-request access token from the atomic session (the wasm stand-in
     * for `AuthApiClient.getAccessToken()` — the library/playback clients
     * share the auth client's session state instead of holding SDK clients).
     */
    protected fun currentToken(): String? = sessionState.currentUser.value?.accessToken

    /** The device id this client authenticates as (transcode-reason session match). */
    protected val deviceId: String get() = identity.deviceId

    protected fun apiUrl(serverAddress: String, path: String): String =
        serverAddress.trimEnd('/') + path

    /** The SDK-identical identity header (no `Token` parameter when null). */
    protected fun HttpRequestBuilder.attachAuthorization(accessToken: String?) {
        headers.append(
            JellyfinAuthorizationHeader.HEADER_NAME,
            JellyfinAuthorizationHeader.build(
                clientName = identity.clientName,
                clientVersion = identity.clientVersion,
                deviceId = identity.deviceId,
                deviceName = identity.deviceName,
                accessToken = accessToken,
            ),
        )
    }

    /** `X-Emby-Token` auth — the raw-token style the JVM OkHttp hand-built requests use. */
    protected fun HttpRequestBuilder.attachEmbyToken(accessToken: String) {
        header("X-Emby-Token", accessToken)
    }

    /**
     * GET decoding the JSON response as [T]. Query is an ordered pair list so
     * repeated keys (ids, fields, sortBy…) serialize exactly like the SDK's
     * multi-value query parameters.
     */
    protected suspend inline fun <reified T> getJson(
        url: String,
        accessToken: String?,
        query: List<Pair<String, String>> = emptyList(),
    ): T = requestJson {
        httpClient.get(url) {
            attachAuthorization(accessToken)
            query.forEach { (k, v) -> parameter(k, v) }
        }
    }

    /**
     * POST (optional pre-encoded JSON body, optional query params), decoding
     * the JSON response as [T]. Bodies are encoded by the caller via
     * [encodeBody] so the serializer is picked at the typed call site — a
     * plain `Any` parameter would resolve against `Any`'s (nonexistent)
     * serializer at runtime.
     */
    protected suspend inline fun <reified T> postForJson(
        url: String,
        accessToken: String?,
        bodyText: String? = null,
        query: List<Pair<String, String>> = emptyList(),
    ): T = requestJson {
        httpClient.post(url) {
            attachAuthorization(accessToken)
            query.forEach { (k, v) -> parameter(k, v) }
            bodyText?.let { setBody(TextContent(it, ContentType.Application.Json)) }
        }
    }

    /** POST whose success depends only on the status code (mutations). */
    protected suspend fun postStatusOnly(
        url: String,
        accessToken: String?,
        bodyText: String? = null,
        query: List<Pair<String, String>> = emptyList(),
    ) {
        val response: HttpResponse = httpClient.post(url) {
            attachAuthorization(accessToken)
            query.forEach { (k, v) -> parameter(k, v) }
            bodyText?.let { setBody(TextContent(it, ContentType.Application.Json)) }
        }
        throwIfFailed(response)
    }

    /** DELETE whose success depends only on the status code. */
    protected suspend fun deleteStatusOnly(
        url: String,
        accessToken: String?,
        query: List<Pair<String, String>> = emptyList(),
    ) {
        val response: HttpResponse = httpClient.delete(url) {
            attachAuthorization(accessToken)
            query.forEach { (k, v) -> parameter(k, v) }
        }
        throwIfFailed(response)
    }

    /** Authenticated GET returning the raw bytes (images, trickplay tiles); null on ANY failure. */
    protected suspend fun getBytes(url: String, accessToken: String?): ByteArray? = try {
        val response: HttpResponse = httpClient.get(url) { attachAuthorization(accessToken) }
        if (response.status.isSuccess()) response.bodyAsBytes() else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Authenticated GET (`X-Emby-Token` style) whose BODY TEXT is needed;
     * null on non-2xx or transport failure. The JVM hand-built OkHttp calls
     * (intro/credit timestamps, remote-subtitle search) treat failure as
     * "no data", never as an error.
     */
    protected suspend fun getBodyTextWithEmbyToken(url: String, accessToken: String): String? = try {
        val response: HttpResponse = httpClient.get(url) { attachEmbyToken(accessToken) }
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    protected inline fun <reified B> encodeBody(body: B): String = wireJson.encodeToString(body)

    // protected (not private): the protected inline reified helpers above
    // must not access less-visible declarations (inline API restriction).
    protected suspend inline fun <reified T> requestJson(
        execute: suspend () -> HttpResponse,
    ): T {
        val response = execute()
        throwIfFailed(response)
        return wireJson.decodeFromString<T>(response.bodyAsText())
    }

    protected fun throwIfFailed(response: HttpResponse) {
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    /** HTTP-status failure → [ApiException], with Retry-After honored. */
    private fun HttpResponse.toApiException(): ApiException = ApiException.fromHttpResponse(
        httpCode = status.value,
        message = friendlyHttpMessage(status.value),
        retryAfterHeader = headers["Retry-After"],
    )

    /**
     * The InvalidStatusException branch of the jvmShared JellyfinErrorMapper,
     * verbatim — wasm callers see the same user-facing texts.
     */
    private fun friendlyHttpMessage(code: Int): String = when (code) {
        401 -> "Authentication required. Please sign in again."
        403 -> "You don't have permission to access this item."
        404 -> "Item not found."
        in 500..599 -> "Server error ($code). Please try again later."
        else -> "Request failed ($code)."
    }

    /**
     * Transport-failure classification, mirroring `ApiException.fromJellyfin`
     * (jvmShared): timeouts and fetch IO errors → retryable with the same
     * friendly texts as JellyfinErrorMapper; everything else non-retryable.
     */
    private fun Throwable.toApiException(): ApiException = when (this) {
        is ApiException -> this
        is HttpRequestTimeoutException -> ApiException(
            isRetryable = true,
            message = "Connection timed out. The server took too long to respond.",
            cause = this,
        )
        is IOException -> ApiException(
            isRetryable = true,
            message = "Network error. Check your connection and try again.",
            cause = this,
        )
        else -> ApiException(
            isRetryable = false,
            message = message ?: "Request failed.",
            cause = this,
        )
    }

    /**
     * The engine's apiResult/apiResultWithRetry pair: failures map onto a
     * typed, pre-classified ApiException. NOTE: recoverCatching swallows the
     * CancellationException rethrow — cancellation surfaces as
     * Result.failure(CancellationException) instead of propagating. That is
     * byte-parity with the JVM engine (JellyfinApiEngine.apiResult has the
     * same shape), so it is kept deliberately rather than "fixed" here.
     */
    protected suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        runCatching { block() }.recoverCatching {
            if (it is CancellationException) throw it
            throw it.toApiException()
        }

    protected suspend fun <T> apiResultWithRetry(
        maxRetries: Int = RetryPolicy.DEFAULT_MAX_RETRIES,
        block: suspend () -> T,
    ): Result<T> = RetryPolicy.executeWithRetry(maxRetries = maxRetries) { apiResult(block) }
}
