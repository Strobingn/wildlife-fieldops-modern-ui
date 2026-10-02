package com.strobingn.wildlifefieldops.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class RoomMigration13To14Test {

    @Test
    fun existingInvoicesStayOperatorOwned() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE invoices (
                        id TEXT NOT NULL PRIMARY KEY,
                        notes TEXT NOT NULL,
                        lineItems TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO invoices (id, notes, lineItems) VALUES ('inv-1', '', '[]')")
            }
            Migrations.MIGRATION_13_14.migrate(supportSqlite(conn))
            val cols = conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(invoices)").use { rs ->
                    val names = mutableSetOf<String>()
                    while (rs.next()) names += rs.getString("name")
                    names
                }
            }
            assertTrue(cols.contains("manuallyEdited"))
            conn.createStatement().use { st ->
                st.executeQuery("SELECT notes, manuallyEdited FROM invoices WHERE id = 'inv-1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("", rs.getString("notes"))
                    assertEquals(1, rs.getInt("manuallyEdited"))
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
