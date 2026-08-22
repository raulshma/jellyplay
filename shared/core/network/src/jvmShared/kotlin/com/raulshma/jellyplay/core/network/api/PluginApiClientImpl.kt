package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.PluginConfigPage
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginInstallationInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.model.PluginVersionInfo
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : PluginApiClient {

    private fun requireServer() = engine.currentServer.value?.address
        ?: throw IllegalStateException("Not connected")

    private fun requireToken() = engine.currentUser.value?.accessToken
        ?: throw IllegalStateException("Not authenticated")

    private fun authRequest(url: String) = Request.Builder()
        .url(url)
        .header("X-Emby-Token", requireToken())

    override suspend fun getInstalledPlugins(): Result<List<PluginInfo>> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to get plugins: ${response.code}")
            val body = response.body ?: return@use emptyList<PluginInfo>()
            // Stream-decode: the plugin list never materializes as a String
            // alongside the decoded objects.
            val json = JellyfinApiEngine.sharedJson.decodeFromStream<JsonArray>(body.byteStream())
            json.mapNotNull { element ->
                try { parsePluginInfo(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun enablePlugin(pluginId: String, version: String): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins/$pluginId/$version/Enable"
        val request = authRequest(url).post("".toRequestBody("application/json".toMediaType())).build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to enable plugin: ${response.code}")
        }
    }

    override suspend fun disablePlugin(pluginId: String, version: String): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins/$pluginId/$version/Disable"
        val request = authRequest(url).post("".toRequestBody("application/json".toMediaType())).build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to disable plugin: ${response.code}")
        }
    }

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins/$pluginId"
        val request = authRequest(url).delete().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to uninstall plugin: ${response.code}")
        }
    }

    override suspend fun getAvailablePackages(): Result<List<PluginPackage>> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Packages"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to get packages: ${response.code}")
            val body = response.body ?: return@use emptyList<PluginPackage>()
            // Stream-decode — the package catalog is the largest plugin payload.
            val json = JellyfinApiEngine.sharedJson.decodeFromStream<JsonArray>(body.byteStream())
            json.mapNotNull { element ->
                try { parsePackageInfo(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun getPackageInfo(name: String, assemblyGuid: String?): Result<PluginPackage> = engine.apiResultWithRetry {
        val urlBuilder = StringBuilder("${requireServer()}/Packages/$name")
        if (assemblyGuid != null) urlBuilder.append("?assemblyGuid=$assemblyGuid")
        val request = authRequest(urlBuilder.toString()).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to get package info: ${response.code}")
            val body = response.body ?: throw Exception("Empty response from server")
            val json = JellyfinApiEngine.sharedJson.decodeFromStream<JsonObject>(body.byteStream())
            parsePackageInfo(json)
        }
    }

    override suspend fun installPackage(
        name: String,
        assemblyGuid: String?,
        version: String?,
        repositoryUrl: String?,
    ): Result<Unit> = engine.apiResultWithRetry {
        val urlBuilder = StringBuilder("${requireServer()}/Packages/Installed/$name?")
        assemblyGuid?.let { urlBuilder.append("assemblyGuid=$it&") }
        version?.let { urlBuilder.append("version=$it&") }
        repositoryUrl?.let { urlBuilder.append("repositoryUrl=$it&") }
        val url = urlBuilder.trimEnd('&', '?').toString()
        val request = authRequest(url).post("".toRequestBody("application/json".toMediaType())).build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to install package: ${response.code}")
        }
    }

    override suspend fun cancelPackageInstallation(packageId: String): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Packages/Installing/$packageId"
        val request = authRequest(url).delete().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to cancel installation: ${response.code}")
        }
    }

    override suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Packages/Installing"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) throw Exception("Failed to get installations: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
            json.mapNotNull { element ->
                try { parseInstallationInfo(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun getRepositories(): Result<List<PluginRepository>> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Repositories"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) throw Exception("Failed to get repositories: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
            json.mapNotNull { element ->
                try { parseRepository(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Repositories"
        val jsonArray = kotlinx.serialization.json.buildJsonArray {
            repositories.forEach { repo ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("Name", repo.name)
                    put("Url", repo.url)
                    put("Enabled", JsonPrimitive(repo.isEnabled))
                })
            }
        }
        val json = JellyfinApiEngine.sharedJson.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), jsonArray)
        val request = authRequest(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to set repositories: ${response.code}")
        }
    }

    override suspend fun getPluginConfiguration(pluginId: String): Result<String> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins/$pluginId/Configuration"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) throw Exception("Failed to get plugin configuration: ${response.code}")
            body
        }
    }

    override suspend fun updatePluginConfiguration(pluginId: String, jsonBody: String): Result<Unit> = engine.apiResultWithRetry {
        val url = "${requireServer()}/Plugins/$pluginId/Configuration"
        val request = authRequest(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to update plugin configuration: ${response.code}")
        }
    }

    override suspend fun getConfigurationPages(): Result<List<PluginConfigPage>> = engine.apiResultWithRetry {
        val url = "${requireServer()}/web/ConfigurationPages"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) throw Exception("Failed to get configuration pages: ${response.code}")
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body)
            json.mapNotNull { element ->
                try { parseConfigPage(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun getDashboardConfigurationPage(name: String): Result<String> = engine.apiResultWithRetry {
        val url = "${requireServer()}/web/ConfigurationPage?name=${java.net.URLEncoder.encode(name, "UTF-8")}"
        val request = authRequest(url).get().build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Failed to get config page: ${response.code}")
            body
        }
    }
}

private fun parsePluginInfo(obj: JsonObject) = PluginInfo(
    id = obj["Id"]?.jsonPrimitive?.content ?: "",
    name = obj["Name"]?.jsonPrimitive?.content ?: "",
    version = obj["Version"]?.jsonPrimitive?.content ?: "",
    description = obj["Description"]?.jsonPrimitive?.content ?: "",
    configurationFileName = obj["ConfigurationFileName"]?.jsonPrimitive?.contentOrNull,
    canUninstall = obj["CanUninstall"]?.jsonPrimitive?.content?.toBoolean() ?: true,
    hasImage = obj["HasImage"]?.jsonPrimitive?.content?.toBoolean() ?: false,
    status = parsePluginStatus(obj["Status"]?.jsonPrimitive?.content ?: "Active"),
)

private fun parsePluginStatus(status: String) = when (status) {
    "Active" -> com.raulshma.jellyplay.core.model.PluginStatus.ACTIVE
    "Restart" -> com.raulshma.jellyplay.core.model.PluginStatus.RESTART
    "Deleted" -> com.raulshma.jellyplay.core.model.PluginStatus.DELETED
    "Superseded", "Superceded" -> com.raulshma.jellyplay.core.model.PluginStatus.SUPERSEDED
    "Malfunctioned" -> com.raulshma.jellyplay.core.model.PluginStatus.MALFUNCTIONED
    "NotSupported" -> com.raulshma.jellyplay.core.model.PluginStatus.NOT_SUPPORTED
    "Disabled" -> com.raulshma.jellyplay.core.model.PluginStatus.DISABLED
    else -> com.raulshma.jellyplay.core.model.PluginStatus.ACTIVE
}

private fun parsePackageInfo(obj: JsonObject) = PluginPackage(
    name = obj["name"]?.jsonPrimitive?.content ?: "",
    description = obj["description"]?.jsonPrimitive?.content ?: "",
    overview = obj["overview"]?.jsonPrimitive?.content ?: "",
    owner = obj["owner"]?.jsonPrimitive?.content ?: "",
    category = obj["category"]?.jsonPrimitive?.content ?: "",
    guid = obj["guid"]?.jsonPrimitive?.content ?: "",
    versions = (obj["versions"]?.jsonArray ?: emptyList()).mapNotNull { v ->
        try { parseVersionInfo(v.jsonObject) } catch (_: Exception) { null }
    },
    imageUrl = obj["imageUrl"]?.jsonPrimitive?.contentOrNull,
)

private fun parseVersionInfo(obj: JsonObject) = PluginVersionInfo(
    version = obj["version"]?.jsonPrimitive?.content ?: "",
    versionNumber = obj["VersionNumber"]?.jsonPrimitive?.content ?: "",
    changelog = obj["changelog"]?.jsonPrimitive?.contentOrNull,
    targetAbi = obj["targetAbi"]?.jsonPrimitive?.contentOrNull,
    sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.contentOrNull,
    checksum = obj["checksum"]?.jsonPrimitive?.contentOrNull,
    timestamp = obj["timestamp"]?.jsonPrimitive?.contentOrNull,
    repositoryName = obj["repositoryName"]?.jsonPrimitive?.content ?: "",
    repositoryUrl = obj["repositoryUrl"]?.jsonPrimitive?.content ?: "",
)

private fun parseRepository(obj: JsonObject) = PluginRepository(
    name = obj["Name"]?.jsonPrimitive?.content ?: "",
    url = obj["Url"]?.jsonPrimitive?.content ?: "",
    isEnabled = obj["Enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true,
)

private fun parseInstallationInfo(obj: JsonObject) = PluginInstallationInfo(
    guid = obj["Guid"]?.jsonPrimitive?.content ?: "",
    name = obj["Name"]?.jsonPrimitive?.contentOrNull,
    version = obj["Version"]?.jsonPrimitive?.contentOrNull,
    changelog = obj["Changelog"]?.jsonPrimitive?.contentOrNull,
)

private fun parseConfigPage(obj: JsonObject) = PluginConfigPage(
    name = obj["Name"]?.jsonPrimitive?.content ?: "",
    displayName = obj["DisplayName"]?.jsonPrimitive?.contentOrNull,
    enableInMainMenu = obj["EnableInMainMenu"]?.jsonPrimitive?.content?.toBoolean() ?: false,
    menuSection = obj["MenuSection"]?.jsonPrimitive?.contentOrNull,
    pluginId = obj["PluginId"]?.jsonPrimitive?.contentOrNull,
)
