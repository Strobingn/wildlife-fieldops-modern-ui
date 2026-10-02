package com.strobingn.wildlifefieldops.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Main after batch 1 + in-app updater is Room 12. Batches 2–5 land at Room 13
 * via a single non-destructive ALTER (MIGRATION_12_13). Batches 3–5 add no
 * further schema versions — extras stay in jobs.pricing jsonb.
 */
class RoomMigrationMainToFinalTest {

    @Test
    fun mainV12MigratesToFinalV13WithoutDroppingRows() {
        assertEquals(13, AppDatabase.VERSION)
        val last = Migrations.ALL.last()
        assertEquals(12, last.startVersion)
        assertEquals(13, last.endVersion)

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        pricing TEXT NOT NULL DEFAULT '{}',
                        confirmedSpecies TEXT NOT NULL DEFAULT '',
                        legalNotes TEXT NOT NULL DEFAULT '',
                        nextStep TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO jobs (id, title, pricing, confirmedSpecies) VALUES ('1', 'Cornwall raccoon', '{}', 'raccoon')")
                st.execute(
                    """
                    CREATE TABLE trap_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        jobId TEXT NOT NULL,
                        trapId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO trap_logs (id, jobId, trapId) VALUES ('t1', '1', 'Deck-1')")
            }
            Migrations.MIGRATION_12_13.migrate(supportSqlite(conn))
            val jobs = columns(conn, "jobs")
            assertTrue(jobs.containsAll(listOf("weatherTrapAdvice", "followUpKind", "followUpDueAt", "followUpNotes", "confirmedSpecies")))
            val traps = columns(conn, "trap_logs")
            assertTrue(traps.containsAll(listOf("disposition", "method")))
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title, confirmedSpecies FROM jobs WHERE id = '1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Cornwall raccoon", rs.getString("title"))
                    assertEquals("raccoon", rs.getString("confirmedSpecies"))
                }
                st.executeQuery("SELECT trapId FROM trap_logs WHERE id = 't1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Deck-1", rs.getString("trapId"))
                }
            }
        }
    }

    @Test
    fun migrationsAreSequentialFrom3ToFinal13() {
        var expected = 3
        Migrations.ALL.forEach { migration: Migration ->
            assertEquals(expected, migration.startVersion)
            assertEquals(expected + 1, migration.endVersion)
            expected = migration.endVersion
        }
        assertEquals(13, expected)
    }

    private fun columns(conn: Connection, table: String): Set<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                val cols = mutableSetOf<String>()
                while (rs.next()) cols += rs.getString("name")
                cols
            }
        }

    private fun supportSqlite(conn: Connection): SupportSQLiteDatabase {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL") {
                conn.createStatement().use { it.execute(args!![0] as String) }
                return@InvocationHandler null
            }
            null
        }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase
    }
}
