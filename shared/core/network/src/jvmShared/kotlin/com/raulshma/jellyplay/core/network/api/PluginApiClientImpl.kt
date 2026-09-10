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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : PluginApiClient {

    private val rawRequester = JellyfinRawRequester(engine)

    override suspend fun getInstalledPlugins(): Result<List<PluginInfo>> = engine.apiResultWithRetry {
        rawRequester.getJson("/Plugins", "Failed to get plugins") { body ->
            if (body == null) {
                emptyList()
            } else {
                // Stream-decode: the plugin list never materializes as a String
                // alongside the decoded objects.
                JellyfinApiEngine.sharedJson.decodeFromStream<JsonArray>(body.byteStream())
                    .mapNotNull { element ->
                        try { parsePluginInfo(element.jsonObject) } catch (_: Exception) { null }
                    }
            }
        }
    }

    override suspend fun enablePlugin(pluginId: String, version: String): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.postStatusOnly("/Plugins/$pluginId/$version/Enable", "Failed to enable plugin")
    }

    override suspend fun disablePlugin(pluginId: String, version: String): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.postStatusOnly("/Plugins/$pluginId/$version/Disable", "Failed to disable plugin")
    }

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.deleteStatusOnly("/Plugins/$pluginId", "Failed to uninstall plugin")
    }

    override suspend fun getAvailablePackages(): Result<List<PluginPackage>> = engine.apiResultWithRetry {
        rawRequester.getJson("/Packages", "Failed to get packages") { body ->
            if (body == null) {
                emptyList()
            } else {
                // Stream-decode — the package catalog is the largest plugin payload.
                JellyfinApiEngine.sharedJson.decodeFromStream<JsonArray>(body.byteStream())
                    .mapNotNull { element ->
                        try { parsePackageInfo(element.jsonObject) } catch (_: Exception) { null }
                    }
            }
        }
    }

    override suspend fun getPackageInfo(name: String, assemblyGuid: String?): Result<PluginPackage> = engine.apiResultWithRetry {
        val query = if (assemblyGuid != null) "?assemblyGuid=$assemblyGuid" else ""
        rawRequester.getJson("/Packages/$name$query", "Failed to get package info") { body ->
            parsePackageInfo(
                JellyfinApiEngine.sharedJson.decodeFromStream<JsonObject>(
                    (body ?: throw Exception("Empty response from server")).byteStream(),
                ),
            )
        }
    }

    override suspend fun installPackage(
        name: String,
        assemblyGuid: String?,
        version: String?,
        repositoryUrl: String?,
    ): Result<Unit> = engine.apiResultWithRetry {
        val pathBuilder = StringBuilder("/Packages/Installed/$name?")
        assemblyGuid?.let { pathBuilder.append("assemblyGuid=$it&") }
        version?.let { pathBuilder.append("version=$it&") }
        repositoryUrl?.let { pathBuilder.append("repositoryUrl=$it&") }
        rawRequester.postStatusOnly(
            path = pathBuilder.trimEnd('&', '?').toString(),
            failureMessage = "Failed to install package",
        )
    }

    override suspend fun cancelPackageInstallation(packageId: String): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.deleteStatusOnly("/Packages/Installing/$packageId", "Failed to cancel installation")
    }

    override suspend fun getPackageInstallations(): Result<List<PluginInstallationInfo>> = engine.apiResultWithRetry {
        rawRequester.getJson("/Packages/Installing", "Failed to get installations") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "[]")
            json.mapNotNull { element ->
                try { parseInstallationInfo(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun getRepositories(): Result<List<PluginRepository>> = engine.apiResultWithRetry {
        rawRequester.getJson("/Repositories", "Failed to get repositories") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "[]")
            json.mapNotNull { element ->
                try { parseRepository(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun setRepositories(repositories: List<PluginRepository>): Result<Unit> = engine.apiResultWithRetry {
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
        rawRequester.postStatusOnly("/Repositories", "Failed to set repositories", json)
    }

    override suspend fun getPluginConfiguration(pluginId: String): Result<String> = engine.apiResultWithRetry {
        rawRequester.getJson("/Plugins/$pluginId/Configuration", "Failed to get plugin configuration") { body ->
            body?.string() ?: "{}"
        }
    }

    override suspend fun updatePluginConfiguration(pluginId: String, jsonBody: String): Result<Unit> = engine.apiResultWithRetry {
        rawRequester.postStatusOnly("/Plugins/$pluginId/Configuration", "Failed to update plugin configuration", jsonBody)
    }

    override suspend fun getConfigurationPages(): Result<List<PluginConfigPage>> = engine.apiResultWithRetry {
        rawRequester.getJson("/web/ConfigurationPages", "Failed to get configuration pages") { body ->
            val json = JellyfinApiEngine.sharedJson.decodeFromString<JsonArray>(body?.string() ?: "[]")
            json.mapNotNull { element ->
                try { parseConfigPage(element.jsonObject) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun getDashboardConfigurationPage(name: String): Result<String> = engine.apiResultWithRetry {
        rawRequester.getJson(
            path = "/web/ConfigurationPage?name=${java.net.URLEncoder.encode(name, "UTF-8")}",
            failureMessage = "Failed to get config page",
        ) { body ->
            body?.string() ?: ""
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
