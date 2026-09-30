package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM stand-in for Room's MigrationTestHelper: apply [Migrations.MIGRATION_9_10]
 * then [Migrations.MIGRATION_10_11] against a v9 SQLite schema and assert columns.
 */
class RoomMigration9To11Test {

    @Test
    fun migratesSyncErrorThenPricingOverrides() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE customers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                st.execute("CREATE TABLE inspections (id TEXT NOT NULL PRIMARY KEY, notes TEXT)")
                st.execute(
                    """
                    CREATE TABLE observation_events (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        entityId TEXT NOT NULL,
                        isSynced INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        estimatedValue REAL NOT NULL DEFAULT 0,
                        county TEXT,
                        state TEXT
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE invoices (
                        id TEXT NOT NULL PRIMARY KEY,
                        jobId TEXT NOT NULL,
                        subtotal REAL NOT NULL DEFAULT 0,
                        taxRate REAL NOT NULL DEFAULT 0,
                        taxAmount REAL NOT NULL DEFAULT 0,
                        discountAmount REAL NOT NULL DEFAULT 0,
                        totalAmount REAL NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }

            val db = supportSqlite(conn)
            Migrations.MIGRATION_9_10.migrate(db)
            val after10 = tableColumns(conn)
            assertTrue("customers.syncError", "syncError" in after10.getValue("customers"))
            assertTrue("inspections.syncError", "syncError" in after10.getValue("inspections"))
            assertTrue("observation_events.syncError", "syncError" in after10.getValue("observation_events"))
            assertTrue("jobs still without pricing at v10", "pricing" !in after10.getValue("jobs"))

            Migrations.MIGRATION_10_11.migrate(db)
            val after11 = tableColumns(conn)
            assertTrue("jobs.pricing", "pricing" in after11.getValue("jobs"))
            assertTrue("invoices.discountPercent", "discountPercent" in after11.getValue("invoices"))
            assertTrue("invoices.subtotalOverride", "subtotalOverride" in after11.getValue("invoices"))
            assertTrue("invoices.taxAmountOverride", "taxAmountOverride" in after11.getValue("invoices"))
            assertTrue("invoices.discountAmountOverride", "discountAmountOverride" in after11.getValue("invoices"))
            assertTrue("invoices.totalOverride", "totalOverride" in after11.getValue("invoices"))
            assertTrue("invoices.taxRateManual", "taxRateManual" in after11.getValue("invoices"))
            // v10 columns still present
            assertTrue("syncError survives 10→11", "syncError" in after11.getValue("customers"))
        }
    }

    private fun tableColumns(conn: Connection): Map<String, Set<String>> {
        val tables = listOf("customers", "inspections", "observation_events", "jobs", "invoices")
        return tables.associateWith { table ->
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info($table)").use { rs ->
                    val cols = mutableSetOf<String>()
                    while (rs.next()) cols += rs.getString("name")
                    cols
                }
            }
        }
    }

    private fun supportSqlite(conn: Connection): SupportSQLiteDatabase {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL") {
                val sql = args!![0] as String
                conn.createStatement().use { it.execute(sql) }
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
