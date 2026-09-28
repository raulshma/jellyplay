package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the backup wire DTOs' round-trips through the exact Json config the
 * client decodes with ([JellyfinApiEngine.sharedJson], ignoreUnknownKeys —
 * the config the raw-path backup calls run through):
 *  1. a full manifest encodes and decodes back equal, and maps onto the
 *     model with every field intact;
 *  2. unknown keys are ignored and absent optionals default — a partial
 *     manifest from a server build that omits fields still decodes;
 *  3. the restore request body serializes the archive file name (the only
 *     field the `POST /Backup/Restore` contract carries).
 */
class BackupWireDtoTest {

    private val json = JellyfinApiEngine.sharedJson

    @Test
    fun `manifest round-trips and maps onto the model`() {
        val body = """
            {"backupEngineVersion":"1.0.0.0","dateCreated":"2026-09-28T10:15:00.0000000Z",
             "options":{"metadata":true,"trickplay":false,"subtitles":true,"database":true},
             "path":"/backups/jellyfin-20260928.zip","serverVersion":"10.11.2"}
        """.trimIndent()

        val dto = json.decodeFromString<BackupManifestDto>(body)

        assertEquals("1.0.0.0", dto.backupEngineVersion)
        assertEquals("2026-09-28T10:15:00.0000000Z", dto.dateCreated)
        assertEquals(true, dto.options?.metadata)
        assertEquals(false, dto.options?.trickplay)
        assertEquals(true, dto.options?.subtitles)
        assertEquals(true, dto.options?.database)
        assertEquals("/backups/jellyfin-20260928.zip", dto.path)
        assertEquals("10.11.2", dto.serverVersion)

        // The wire names are the DTO property names (the classes ride the
        // SDK's Json, no custom naming strategy) — round-trip keeps equality.
        assertEquals(dto, json.decodeFromString<BackupManifestDto>(json.encodeToString(BackupManifestDto.serializer(), dto)))

        val model = dto.toServerBackup()
        assertEquals("1.0.0.0", model.backupEngineVersion)
        assertEquals("2026-09-28T10:15:00.0000000Z", model.dateCreated)
        assertEquals(
            BackupComponentOptions(metadata = true, trickplay = false, subtitles = true, database = true),
            model.options,
        )
        assertEquals("/backups/jellyfin-20260928.zip", model.path)
        assertEquals("10.11.2", model.serverVersion)
    }

    @Test
    fun `unknown keys are ignored and absent optionals default`() {
        val decoded = json.decodeFromString<BackupManifestDto>(
            """{"path":"/b/x.zip","someFutureField":123}""",
        )

        assertEquals("/b/x.zip", decoded.path)
        assertEquals(null, decoded.backupEngineVersion)
        assertEquals(null, decoded.options)
        assertEquals(null, decoded.dateCreated)
        assertEquals(null, decoded.serverVersion)

        // The mapper folds the nulls into the model's defaults.
        val model = decoded.toServerBackup()
        assertEquals(BackupComponentOptions(), model.options)
        assertEquals("/b/x.zip", model.path)
        assertEquals("", model.backupEngineVersion)
    }

    @Test
    fun `a malformed manifest fails the decode`() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<BackupManifestDto>("not json")
        }
    }

    @Test
    fun `restore request serializes the archive file name`() {
        val dto = BackupRestoreRequestDto(archiveFileName = "jellyfin-20260928.zip")

        val encoded = json.encodeToString(BackupRestoreRequestDto.serializer(), dto)

        assertEquals("""{"archiveFileName":"jellyfin-20260928.zip"}""", encoded)
        assertEquals(dto, json.decodeFromString<BackupRestoreRequestDto>(encoded))
    }

    @Test
    fun `options dto round-trips all four components`() {
        val dto = BackupOptionsDto(metadata = true, trickplay = false, subtitles = true, database = false)

        assertEquals(dto, json.decodeFromString<BackupOptionsDto>(json.encodeToString(BackupOptionsDto.serializer(), dto)))
    }
}
