package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.ui.graphics.Color
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.StatusInProgress
import com.strobingn.wildlifefieldops.ui.theme.StatusPending
import com.strobingn.wildlifefieldops.ui.theme.SuccessGreen

fun jobStatusColor(status: JobStatus): Color = when (com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.flag(status)) {
    JobStatus.INSPECTION -> AccentBlue
    JobStatus.IN_PROGRESS -> StatusInProgress
    JobStatus.COMPLETED -> SuccessGreen
    else -> StatusPending
}

/** Map pins use one gray per flag. Completed is the green dot; an inspection is blue. */
internal fun markerArgb(status: JobStatus): Int = when (com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.flag(status)) {
    JobStatus.INSPECTION -> android.graphics.Color.rgb(66, 133, 244)
    JobStatus.SCHEDULED -> android.graphics.Color.rgb(214, 214, 214)
    JobStatus.IN_PROGRESS -> android.graphics.Color.rgb(140, 140, 140)
    else -> android.graphics.Color.rgb(67, 160, 71)
}
