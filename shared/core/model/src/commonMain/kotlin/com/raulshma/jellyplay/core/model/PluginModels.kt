package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class PluginStatus {
    @Serializable
    ACTIVE,

    @Serializable
    RESTART,

    @Serializable
    DELETED,

    @Serializable
    SUPERSEDED,

    @Serializable
    MALFUNCTIONED,

    @Serializable
    NOT_SUPPORTED,

    @Serializable
    DISABLED,
}

@Immutable
@Serializable
data class PluginInfo(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val configurationFileName: String? = null,
    val canUninstall: Boolean = true,
    val hasImage: Boolean = false,
    val status: PluginStatus = PluginStatus.ACTIVE,
)

@Immutable
@Serializable
data class PluginVersionInfo(
    val version: String = "",
    val versionNumber: String = "",
    val changelog: String? = null,
    val targetAbi: String? = null,
    val sourceUrl: String? = null,
    val checksum: String? = null,
    val timestamp: String? = null,
    val repositoryName: String = "",
    val repositoryUrl: String = "",
)

@Immutable
@Serializable
data class PluginPackage(
    val name: String = "",
    val description: String = "",
    val overview: String = "",
    val owner: String = "",
    val category: String = "",
    val guid: String = "",
    val versions: List<PluginVersionInfo> = emptyList(),
    val imageUrl: String? = null,
) {
    val latestVersion: PluginVersionInfo? get() = versions.firstOrNull()

    val isInstalled: Boolean get() = false
}

@Immutable
@Serializable
data class PluginRepository(
    val name: String = "",
    val url: String = "",
    val isEnabled: Boolean = true,
)

@Immutable
@Serializable
data class PluginInstallationInfo(
    val guid: String = "",
    val name: String? = null,
    val version: String? = null,
    val changelog: String? = null,
)

@Immutable
@Serializable
data class PluginConfigPage(
    val name: String = "",
    val displayName: String? = null,
    val enableInMainMenu: Boolean = false,
    val menuSection: String? = null,
    val pluginId: String? = null,
)
