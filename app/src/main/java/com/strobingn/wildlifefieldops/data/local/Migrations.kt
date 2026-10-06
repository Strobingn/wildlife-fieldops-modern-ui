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
 *
 * MIGRATION_5_6 adds `voice_observations` for fail-closed voice-first logging notes.
 *
 * MIGRATION_6_7 adds append-only `observation_events` for on-device species IDs (ADR 0002).
 *
 * MIGRATION_7_8 adds local sync flags on `observation_events` (evidence rows stay append-only).
 *
 * MIGRATION_8_9 adds `sync_operations` — the durable domain ledger for the
 * WorkManager 2.12 sync canary (ADR 0004). Distinct from WM analytics (~7 days).
 *
 * MIGRATION_9_10 adds nullable `syncError` on customers, inspections, and
 * observation_events so failed cloud writes stay on-device with a reason.
 * Existing rows and photo files are preserved (ALTER TABLE ADD COLUMN only).
 *
 * MIGRATION_10_11 persists the estimate worksheet and computed-field overrides
 * on jobs (`pricing` JSON) and invoices (nullable override columns). Existing
 * rows stay valid: empty pricing JSON and NULL overrides mean "use calculated".
 *
 * MIGRATION_11_12 adds FieldOps AI columns on jobs and inspections. Existing
 * rows stay valid (empty strings / NULL due). Values also ride in
 * `jobs.pricing` jsonb and `inspections.findings` jsonb for live sync.
 *
 * MIGRATION_12_13 adds DEC fields on trap_logs and weather/follow-up columns
 * on jobs. Values also ride in `jobs.pricing` jsonb (trapRecords, advice,
 * follow-up) so live PostgREST does not need dedicated columns.
 *
 * MIGRATION_13_14 marks existing invoices as operator-owned (`manuallyEdited = 1`)
 * so estimate carry never refills a saved invoice. New untouched carries may set 0.
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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS voice_observations (
                    id TEXT NOT NULL PRIMARY KEY,
                    jobId TEXT,
                    observationEventId TEXT,
                    audioLocalPath TEXT NOT NULL,
                    audioSha256 TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    validatedTranscript TEXT NOT NULL,
                    editedTranscript TEXT NOT NULL,
                    winningAttemptId TEXT NOT NULL,
                    observedAt INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS observation_events (
                    eventId TEXT NOT NULL PRIMARY KEY,
                    entityId TEXT NOT NULL,
                    observedAt INTEGER NOT NULL,
                    uploadedAt INTEGER NOT NULL,
                    deviceId TEXT NOT NULL,
                    operatorId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    modelHash TEXT NOT NULL,
                    backendTag TEXT NOT NULL,
                    quantizerTag TEXT NOT NULL,
                    frameHash TEXT NOT NULL,
                    cropHash TEXT NOT NULL,
                    mediaUri TEXT,
                    labelDistributionJson TEXT NOT NULL,
                    captureQuality REAL NOT NULL,
                    geometryTrust REAL NOT NULL,
                    humanVerification TEXT NOT NULL,
                    supersedesEventId TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_observation_events_entityId_observedAt ON observation_events(entityId, observedAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_observation_events_humanVerification ON observation_events(humanVerification)"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE observation_events ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE observation_events ADD COLUMN syncedAt INTEGER")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_operations (
                    operationId TEXT NOT NULL PRIMARY KEY,
                    idempotencyKey TEXT NOT NULL,
                    state TEXT NOT NULL,
                    workRequestId TEXT,
                    workGeneration INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sync_operations_idempotencyKey ON sync_operations(idempotencyKey)"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE customers ADD COLUMN syncError TEXT")
            db.execSQL("ALTER TABLE inspections ADD COLUMN syncError TEXT")
            db.execSQL("ALTER TABLE observation_events ADD COLUMN syncError TEXT")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN pricing TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE invoices ADD COLUMN discountPercent REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE invoices ADD COLUMN subtotalOverride REAL")
            db.execSQL("ALTER TABLE invoices ADD COLUMN taxAmountOverride REAL")
            db.execSQL("ALTER TABLE invoices ADD COLUMN discountAmountOverride REAL")
            db.execSQL("ALTER TABLE invoices ADD COLUMN totalOverride REAL")
            db.execSQL("ALTER TABLE invoices ADD COLUMN taxRateManual INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN confirmedSpecies TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN legalNotes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN nextStep TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN nextStepDueAt INTEGER")
            db.execSQL("ALTER TABLE jobs ADD COLUMN nextStepSource TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN aiRuntime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE inspections ADD COLUMN aiNarrativeDraft TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE inspections ADD COLUMN aiDraftSource TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trap_logs ADD COLUMN disposition TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trap_logs ADD COLUMN method TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN weatherTrapAdvice TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN followUpKind TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN followUpDueAt INTEGER")
            db.execSQL("ALTER TABLE jobs ADD COLUMN followUpNotes TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN manuallyEdited INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * MIGRATION_14_15 adds a nullable per-trap check interval (hours) on trap_logs.
     * Null means "use the Settings default", so existing traps keep 24-hour checks.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trap_logs ADD COLUMN checkIntervalHours INTEGER")
        }
    }

    /** Ordered 3→15. Restored backups may be older than VERSION 15; Room must migrate, never wipe. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
    )
}
