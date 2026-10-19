package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration15To16Test {

    @Test
    fun addsPreciseGpsColumnsWithoutTouchingTraps() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE trap_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        jobId TEXT NOT NULL,
                        trapId TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        checkIntervalHours INTEGER
                    )
                    """.trimIndent()
                )
                st.execute(
                    "INSERT INTO trap_logs (id, jobId, trapId, latitude, longitude, checkIntervalHours) " +
                        "VALUES ('t1', 'j1', 'Cage 1', 41.44, -74.01, 24)"
                )
            }
            Migrations.MIGRATION_15_16.migrate(supportSqlite(conn))
            assertEquals(15, Migrations.MIGRATION_15_16.startVersion)
            assertEquals(16, Migrations.MIGRATION_15_16.endVersion)
            assertTrue(Migrations.ALL.any { it.startVersion == 15 && it.endVersion == 16 })
            val cols = conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(trap_logs)").use { rs ->
                    val out = mutableSetOf<String>()
                    while (rs.next()) out += rs.getString("name")
                    out
                }
            }
            assertTrue("locationAccuracyM" in cols)
            assertTrue("locationSampleCount" in cols)
            assertTrue("locationCapturedAt" in cols)
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT trapId, latitude, longitude, locationAccuracyM, locationSampleCount, locationCapturedAt " +
                        "FROM trap_logs WHERE id = 't1'"
                ).use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Cage 1", rs.getString("trapId"))
                    assertEquals(41.44, rs.getDouble("latitude"), 0.0)
                    assertEquals(-74.01, rs.getDouble("longitude"), 0.0)
                    rs.getObject("locationAccuracyM")
                    assertTrue(rs.wasNull())
                    rs.getObject("locationSampleCount")
                    assertTrue(rs.wasNull())
                    rs.getObject("locationCapturedAt")
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
