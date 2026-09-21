package com.strobingn.wildlifefieldops.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for the Wildlife FieldOps database.
 *
 * MIGRATION_3_4 adds the `county` and `state` columns to the `jobs` table so that
 * resolved NY county information can be persisted for offline invoice use.
 *
 * MIGRATION_4_5 adds `field_observations` for offline map pins (photo + GPS + note).
 */
object Migrations {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN county TEXT")
            db.execSQL("ALTER TABLE jobs ADD COLUMN state TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS field_observations (
                    id TEXT NOT NULL PRIMARY KEY,
                    notes TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    photoLocalPath TEXT NOT NULL,
                    photoId TEXT,
                    jobId TEXT,
                    speciesHint TEXT NOT NULL,
                    accuracyMeters REAL,
                    observedAt INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL,
                    syncError TEXT
                )
                """.trimIndent()
            )
        }
    }
}
