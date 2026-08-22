package com.raulshma.jellyplay.core.database.di

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Phase C4 smoke: databaseDaosModule over desktopDatabaseModule resolves the
 * database singleton and a working DAO (local KoinApplication, not the
 * global context).
 */
class DatabaseKoinModulesTest {

    @Test
    fun `dao module over desktop database resolves and round-trips`() = runTest {
        val dir = createTempDirectory("jellyplay-database-koin")
        val dbPath = dir.resolve("jellyplay.db").toString().toPath()
        val app = startKoin {
            modules(databaseDaosModule, desktopDatabaseModule(dbPath))
        }
        val database = app.koin.get<JellyPlayDatabase>()
        try {
            assertSame(database, app.koin.get<JellyPlayDatabase>(), "database must be a single Koin instance")

            val dao = app.koin.get<ServerDao>()
            dao.insertServer(
                ServerEntity(id = "s1", name = "Koin", address = "https://jellyfin.example"),
            )
            val loaded = dao.getServerById("s1")
            assertNotNull(loaded)
            assertEquals("Koin", loaded!!.name)
        } finally {
            database.close()
            stopKoin()
            dir.toFile().deleteRecursively()
        }
    }
}
