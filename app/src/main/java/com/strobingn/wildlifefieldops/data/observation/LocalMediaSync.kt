package com.strobingn.wildlifefieldops.data.observation

/**
 * Offline media queue decision. A local file that cannot be read stays queued
 * with an error. Remote URLs and empty paths sync as metadata only.
 */
object LocalMediaSync {
    enum class Plan {
        /** No local file. Metadata may sync. */
        ABSENT,
        /** Bytes are readable. Upload once, then ACK. */
        UPLOAD,
        /** Path is local but the file is missing. Do not ACK. */
        MISSING
    }

    fun plan(pathOrUri: String?, bytesAvailable: Boolean): Plan {
        if (!ObservationPhotoPaths.isLocalCandidate(pathOrUri)) return Plan.ABSENT
        return if (bytesAvailable) Plan.UPLOAD else Plan.MISSING
    }
}
