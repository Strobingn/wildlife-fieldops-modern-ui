package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration14To15Test {

    @Test
    fun addsTrapCheckIntervalWithoutTouchingTraps() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE trap_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        jobId TEXT NOT NULL,
                        trapId TEXT NOT NULL,
                        nextCheckDate INTEGER
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO trap_logs (id, jobId, trapId, nextCheckDate) VALUES ('t1', 'j1', 'Cage 1', 1700000000000)")
            }
            Migrations.MIGRATION_14_15.migrate(supportSqlite(conn))
            assertEquals(14, Migrations.MIGRATION_14_15.startVersion)
            assertEquals(15, Migrations.MIGRATION_14_15.endVersion)
            assertTrue(Migrations.ALL.any { it.startVersion == 14 && it.endVersion == 15 })
            val cols = conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(trap_logs)").use { rs ->
                    val out = mutableSetOf<String>()
                    while (rs.next()) out += rs.getString("name")
                    out
                }
            }
            assertTrue("checkIntervalHours" in cols)
            conn.createStatement().use { st ->
                st.executeQuery("SELECT trapId, nextCheckDate, checkIntervalHours FROM trap_logs WHERE id = 't1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Cage 1", rs.getString("trapId"))
                    assertEquals(1700000000000L, rs.getLong("nextCheckDate"))
                    rs.getObject("checkIntervalHours")
                    assertTrue(rs.wasNull())
                }
            }
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
