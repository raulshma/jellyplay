package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.library.BaseItemQueryResultDtoWire
import com.raulshma.jellyplay.core.network.library.toLibraryFolder
import com.raulshma.jellyplay.core.network.user.CreateUserByNameRequestDtoWire
import com.raulshma.jellyplay.core.network.user.ManagedUserDtoWire
import com.raulshma.jellyplay.core.network.user.ManagedUserPolicyDtoWire
import com.raulshma.jellyplay.core.network.user.ParentalRatingDtoWire
import com.raulshma.jellyplay.core.network.user.UpdateUserPasswordRequestDtoWire
import com.raulshma.jellyplay.core.network.user.overlayWith
import com.raulshma.jellyplay.core.network.user.toManagedUser
import com.raulshma.jellyplay.core.network.user.toParentalRatingOptions
import com.raulshma.jellyplay.core.network.userPostWireJson
import io.ktor.client.HttpClient
import kotlinx.serialization.encodeToString

/**
 * The wasmJs [UserApiClient] — a hand-rolled Ktor replacement for the
 * jvmShared `UserApiClientImpl` (Jellyfin SDK + OkHttp). Endpoint paths and
 * semantics mirror the JVM implementation request-for-request:
 *
 *  - `getUsers`/`getUserById`/`getCurrentUser` → GET /Users, /Users/{id},
 *    /Users/Me (SDK `UserApi`, no query args — the SDK's nullable
 *    isHidden/isDisabled are omitted when null).
 *  - `createUser` → POST /Users/New (`CreateUserByName`: Name + Password).
 *  - `renameUser` → GET the full UserDto → copy(Name) → POST the whole DTO
 *    to /Users?userId={id} (the SDK passes userId as a QUERY param there,
 *    not a path segment) → re-GET → map. The wire DTO round-trips the full
 *    schema — including the raw `Configuration` element — so nothing the
 *    server sent is dropped, matching the JVM's "never construct a partial
 *    UserDto" rule.
 *  - `updateUserPolicy` → GET UserDto → take the server policy (or the
 *    all-false fallback — [ManagedUserPolicyDtoWire]'s defaults ARE the
 *    jvmShared fallback construction) → `overlayWith(edited, userId)` →
 *    POST the merged full policy to /Users/{id}/Policy.
 *  - `updateUserPassword` → POST /Users/Password?userId={id} with
 *    CurrentPw=null, NewPw=newPassword, ResetPassword=(newPassword == null)
 *    — resetting when no new password is given.
 *  - `deleteUser` → DELETE /Users/{id}.
 *  - `getLibraryFoldersForEditor` → GET /Library/MediaFolders (admin-only,
 *    unfiltered — NOT the session-filtered /UserViews the library client's
 *    getLibraryFolders uses), mapped through the shared
 *    [toLibraryFolder] wire mapper.
 *  - `getParentalRatings` → GET /Localization/ParentalRatings, grouped by
 *    score+subScore via the shared [toParentalRatingOptions] mapper.
 *
 * Zero semantic deltas vs jvmShared by design. The only wire-level notes:
 * date fields keep the server's raw strings (the wasm convention — jvmShared
 * re-formats through the SDK DateTime), and the rename/policy POST bodies are
 * encoded with `encodeDefaults = true` ([userPostWireJson]) because the SDK
 * DTOs have no field defaults — the JVM always writes every field, false
 * included, and omitting a `false` permission would let the server's own
 * permissive defaults resurrect it.
 */
class KtorWasmUserApiClient(
    httpClient: HttpClient,
    sessionState: AtomicSessionState,
    identity: WasmClientIdentity,
) : WasmApiSupport(httpClient, sessionState, identity), UserApiClient {

    override suspend fun getManagedUsers(): Result<List<ManagedUser>> = apiResultWithRetry {
        val server = requireConnectedServer()
        val users = getJson<List<ManagedUserDtoWire>>(
            url = apiUrl(server.address, "/Users"),
            accessToken = currentToken(),
        )
        users.map { it.toManagedUser() }
    }

    override suspend fun getManagedUser(userId: String): Result<ManagedUser> = apiResultWithRetry {
        val server = requireConnectedServer()
        getJson<ManagedUserDtoWire>(
            url = apiUrl(server.address, "/Users/$userId"),
            accessToken = currentToken(),
        ).toManagedUser()
    }

    override suspend fun getCurrentUserId(): Result<String> = apiResultWithRetry {
        val server = requireConnectedServer()
        getJson<ManagedUserDtoWire>(
            url = apiUrl(server.address, "/Users/Me"),
            accessToken = currentToken(),
        ).id ?: ""
    }

    override suspend fun getCurrentUser(): Result<ManagedUser> = apiResultWithRetry {
        val server = requireConnectedServer()
        getJson<ManagedUserDtoWire>(
            url = apiUrl(server.address, "/Users/Me"),
            accessToken = currentToken(),
        ).toManagedUser()
    }

    override suspend fun createUser(name: String, password: String?): Result<ManagedUser> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            postForJson<ManagedUserDtoWire>(
                url = apiUrl(server.address, "/Users/New"),
                accessToken = currentToken(),
                bodyText = encodeBody(
                    CreateUserByNameRequestDtoWire(name = name, password = password),
                ),
            ).toManagedUser()
        }

    override suspend fun renameUser(userId: String, newName: String): Result<ManagedUser> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            // Fetch the full DTO, copy the name, POST it back. Never construct a
            // partial UserDto — dropping policy/configuration would clear them
            // server-side (the wire DTO round-trips every field it decoded).
            val current = getJson<ManagedUserDtoWire>(
                url = apiUrl(server.address, "/Users/$userId"),
                accessToken = currentToken(),
            )
            val renamed = current.copy(name = newName)
            postStatusOnly(
                url = apiUrl(server.address, "/Users"),
                accessToken = currentToken(),
                // Full-DTO write: every field must land on the wire (see
                // userPostWireJson) or the server would reset the omitted ones.
                bodyText = userPostWireJson.encodeToString(renamed),
                query = listOf("userId" to userId),
            )
            getJson<ManagedUserDtoWire>(
                url = apiUrl(server.address, "/Users/$userId"),
                accessToken = currentToken(),
            ).toManagedUser()
        }

    override suspend fun updateUserPolicy(
        userId: String,
        policy: ManagedUserPolicy,
    ): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        // Rehydrate the full server policy, overlay the edited fields, POST
        // the merged object. Preserves all bookkeeping fields. A missing
        // server policy falls back to the all-off wire default, whose field
        // values are exactly the jvmShared fallback construction.
        val current = getJson<ManagedUserDtoWire>(
            url = apiUrl(server.address, "/Users/$userId"),
            accessToken = currentToken(),
        )
        val serverPolicy = current.policy ?: ManagedUserPolicyDtoWire()
        val merged = serverPolicy.overlayWith(policy, userId)
        postStatusOnly(
            url = apiUrl(server.address, "/Users/$userId/Policy"),
            accessToken = currentToken(),
            // Full-policy write: an edited-off permission is often `false` —
            // the wire default — so this body MUST encode defaults or the
            // server would silently restore its permissive CLR default.
            bodyText = userPostWireJson.encodeToString(merged),
        )
    }

    override suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            postStatusOnly(
                url = apiUrl(server.address, "/Users/Password"),
                accessToken = currentToken(),
                bodyText = encodeBody(
                    UpdateUserPasswordRequestDtoWire(
                        currentPw = null,
                        newPw = newPassword,
                        resetPassword = newPassword == null,
                    ),
                ),
                query = listOf("userId" to userId),
            )
        }

    override suspend fun deleteUser(userId: String): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        deleteStatusOnly(
            url = apiUrl(server.address, "/Users/$userId"),
            accessToken = currentToken(),
        )
    }

    override suspend fun getLibraryFoldersForEditor(): Result<List<LibraryFolder>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val response = getJson<BaseItemQueryResultDtoWire?>(
                url = apiUrl(server.address, "/Library/MediaFolders"),
                accessToken = currentToken(),
            ) ?: throw IllegalStateException("Server returned empty response")
            response.items.map { it.toLibraryFolder() }
            // NOTE: deliberately NOT filtered by the current user's
            // enabledFolderIds — the editor needs the full server folder list
            // (and /Library/MediaFolders is the admin-only unfiltered source).
        }

    override suspend fun getParentalRatings(): Result<List<ParentalRatingOption>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val ratings = getJson<List<ParentalRatingDtoWire>>(
                url = apiUrl(server.address, "/Localization/ParentalRatings"),
                accessToken = currentToken(),
            )
            ratings.toParentalRatingOptions()
        }
}
