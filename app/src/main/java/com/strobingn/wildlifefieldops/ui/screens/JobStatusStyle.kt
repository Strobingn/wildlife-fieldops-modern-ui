package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.ui.graphics.Color
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.AccentPurple
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusInProgress
import com.strobingn.wildlifefieldops.ui.theme.StatusPending
import com.strobingn.wildlifefieldops.ui.theme.SuccessGreen

fun jobStatusColor(status: JobStatus): Color = when (status) {
    JobStatus.PENDING, JobStatus.LEAD, JobStatus.ESTIMATE_SENT -> StatusPending
    JobStatus.SCHEDULED, JobStatus.IN_PROGRESS, JobStatus.TRAPPING, JobStatus.EXCLUSION -> StatusInProgress
    JobStatus.COMPLETED, JobStatus.CLOSED -> SuccessGreen
    JobStatus.CANCELLED -> ErrorRed
    JobStatus.INVOICED -> AccentPurple
    JobStatus.PAID -> PrimaryGreen
}

/** Map pins stay greyscale. Semantic red/green remain for cancelled and paid. */
internal fun markerArgb(status: JobStatus): Int = when (status) {
    JobStatus.PENDING, JobStatus.LEAD, JobStatus.ESTIMATE_SENT -> android.graphics.Color.rgb(214, 214, 214)
    JobStatus.SCHEDULED, JobStatus.IN_PROGRESS -> android.graphics.Color.rgb(176, 176, 176)
    JobStatus.TRAPPING, JobStatus.EXCLUSION -> android.graphics.Color.rgb(140, 140, 140)
    JobStatus.COMPLETED, JobStatus.CLOSED -> android.graphics.Color.rgb(67, 160, 71)
    JobStatus.INVOICED -> android.graphics.Color.rgb(120, 120, 120)
    JobStatus.PAID -> android.graphics.Color.rgb(90, 90, 90)
    JobStatus.CANCELLED -> android.graphics.Color.rgb(229, 57, 53)
}
