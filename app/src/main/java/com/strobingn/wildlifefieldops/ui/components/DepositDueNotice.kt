package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.strobingn.wildlifefieldops.pricing.DepositDue
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed

/**
 * Same sentence the estimate and invoice PDFs print under the total.
 * The PDF keeps [DepositDue.COLOR] on white paper. This card is a light wash
 * on a dark page in the default theme, so the sentence uses [ErrorRed], which
 * is a light red there and a dark red on the light theme.
 */
@Composable
fun DepositDueNotice(total: Double, modifier: Modifier = Modifier) {
    val text = DepositDue.notice(total) ?: return
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = ErrorRed,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.End
    )
}
