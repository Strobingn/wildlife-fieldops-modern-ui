package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration12To13Test {

    @Test
    fun addsTrapAndFollowUpColumnsWithoutDroppingRows() {
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
                    CREATE TABLE trap_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        jobId TEXT NOT NULL,
                        trapId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO trap_logs (id, jobId, trapId) VALUES ('t1', '1', 'Deck-1')")
            }
            val db = supportSqlite(conn)
            Migrations.MIGRATION_12_13.migrate(db)
            val jobs = columns(conn, "jobs")
            assertTrue("weatherTrapAdvice" in jobs)
            assertTrue("followUpKind" in jobs)
            assertTrue("followUpDueAt" in jobs)
            assertTrue("followUpNotes" in jobs)
            val traps = columns(conn, "trap_logs")
            assertTrue("disposition" in traps)
            assertTrue("method" in traps)
            conn.createStatement().use { st ->
                st.executeQuery("SELECT title FROM jobs WHERE id = '1'").use { rs ->
                    assertTrue(rs.next())
                    assertTrue(rs.getString("title") == "keep me")
                }
                st.executeQuery("SELECT trapId FROM trap_logs WHERE id = 't1'").use { rs ->
                    assertTrue(rs.next())
                    assertTrue(rs.getString("trapId") == "Deck-1")
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
