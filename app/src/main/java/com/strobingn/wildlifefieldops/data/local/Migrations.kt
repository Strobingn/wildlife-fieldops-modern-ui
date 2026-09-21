package com.strobingn.wildlifefieldops.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for the Wildlife FieldOps database.
 *
 * MIGRATION_3_4 adds the `county` and `state` columns to the `jobs` table so that
 * resolved NY county information can be persisted for offline invoice use.
 */
object Migrations {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN county TEXT")
            db.execSQL("ALTER TABLE jobs ADD COLUMN state TEXT")
        }
    }
}
