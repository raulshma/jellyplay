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

    // ── decodeBackupManifests — the key-alias page decode ───────────────────
    // The strict DTO decode silently null-defaulted every field once the
    // server's manifest keys drifted (the blank backup card); the page decode
    // reads each entry through alias lists instead.

    @Test
    fun `page decode resolves the 10_11 manifest keys`() {
        val backups = decodeBackupManifests(
            """[{"backupEngineVersion":"1.0.0.0","dateCreated":"2026-09-28T10:15:00.0000000Z",
                 "options":{"metadata":true,"trickplay":false,"subtitles":true,"database":true},
                 "path":"/backups/jellyfin-20260928.zip","serverVersion":"10.11.2"}]""",
        )

        val model = backups.single().toServerBackup()
        assertEquals("1.0.0.0", model.backupEngineVersion)
        assertEquals("2026-09-28T10:15:00.0000000Z", model.dateCreated)
        assertEquals("/backups/jellyfin-20260928.zip", model.path)
        assertEquals("10.11.2", model.serverVersion)
        assertEquals(
            BackupComponentOptions(metadata = true, trickplay = false, subtitles = true, database = true),
            model.options,
        )
    }

    @Test
    fun `page decode resolves the real 12_x PascalCase manifest`() {
        // Verbatim shape of a Jellyfin 12.1.0 GET /Backup page (PascalCase
        // keys — the shape the strict camelCase DTO decode blank-carded).
        val backups = decodeBackupManifests(
            """[{"ServerVersion":"12.1.0.0","BackupEngineVersion":"0.2.0",
                 "DateCreated":"2026-09-27T21:36:30.9857855+00:00",
                 "Path":"/config/data/backups/jellyfin-backup-20260928030630.zip",
                 "Options":{"Metadata":false,"Trickplay":false,"Subtitles":false,"Database":true}}]""",
        )

        val model = backups.single().toServerBackup()
        assertEquals("0.2.0", model.backupEngineVersion)
        assertEquals("2026-09-27T21:36:30.9857855+00:00", model.dateCreated)
        assertEquals("/config/data/backups/jellyfin-backup-20260928030630.zip", model.path)
        assertEquals("12.1.0.0", model.serverVersion)
        assertEquals(
            BackupComponentOptions(metadata = false, trickplay = false, subtitles = false, database = true),
            model.options,
        )
    }

    @Test
    fun `page decode resolves renamed 12_x keys and a component list`() {
        // A drifted-generation manifest: none of the 10.11 keys survive, and
        // the component block became a name list. Every field must still land.
        val backups = decodeBackupManifests(
            """[{"fileName":"jellyfin-20260930.zip","createdAt":"2026-09-30T08:00:00Z",
                 "components":["Metadata","Database"],"jellyfinVersion":"12.1.0"}]""",
        )

        val model = backups.single().toServerBackup()
        assertEquals("jellyfin-20260930.zip", model.path)
        assertEquals("2026-09-30T08:00:00Z", model.dateCreated)
        assertEquals("12.1.0", model.serverVersion)
        assertEquals(
            BackupComponentOptions(metadata = true, trickplay = false, subtitles = false, database = true),
            model.options,
        )
    }

    @Test
    fun `page decode tolerates a paged envelope and drops empty entries`() {
        val backups = decodeBackupManifests(
            """{"items":[{"path":"kept.zip"},{"unknownKey":42}]}""",
        )

        assertEquals(listOf("kept.zip"), backups.map { it.path })
    }

    @Test
    fun `page decode folds malformed bodies and non-array roots to empty`() {
        assertEquals(emptyList(), decodeBackupManifests("not json"))
        assertEquals(emptyList(), decodeBackupManifests("42"))
        assertEquals(emptyList(), decodeBackupManifests(""))
    }
}
