package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration11To12Test {

    @Test
    fun addsFieldOpsColumnsWithoutDroppingJobs() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        pricing TEXT NOT NULL DEFAULT '{}'
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO jobs (id, title, pricing) VALUES ('1', 'keep me', '{}')")
                st.execute(
                    """
                    CREATE TABLE inspections (
                        id TEXT NOT NULL PRIMARY KEY,
                        findings TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO inspections (id, findings) VALUES ('i1', 'old findings')")
            }
            val db = supportSqlite(conn)
            Migrations.MIGRATION_11_12.migrate(db)
            val jobs = columns(conn, "jobs")
            assertTrue("confirmedSpecies" in jobs)
            assertTrue("legalNotes" in jobs)
            assertTrue("nextStep" in jobs)
            assertTrue("nextStepDueAt" in jobs)
            assertTrue("nextStepSource" in jobs)
            assertTrue("aiRuntime" in jobs)
            val inspections = columns(conn, "inspections")
            assertTrue("aiNarrativeDraft" in inspections)
            assertTrue("aiDraftSource" in inspections)
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title FROM jobs WHERE id = '1'").use { rs ->
                    assertTrue(rs.next())
                    assertTrue(rs.getString("title") == "keep me")
                }
                st.executeQuery("SELECT findings FROM inspections WHERE id = 'i1'").use { rs ->
                    assertTrue(rs.next())
                    assertTrue(rs.getString("findings") == "old findings")
                }
            }
        }
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
