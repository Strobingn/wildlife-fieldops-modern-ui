package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.strobingn.wildlifefieldops.pricing.DepositDue
import com.strobingn.wildlifefieldops.util.DocumentPalette

/** Same sentence the estimate and invoice PDFs print under the total. */
@Composable
fun DepositDueNotice(total: Double, modifier: Modifier = Modifier) {
    val text = DepositDue.notice(total) ?: return
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = depositRed(),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.End
    )
}

private fun depositRed(): Color {
    val rgb = DocumentPalette.rgb(DepositDue.COLOR)
    return Color(rgb[0], rgb[1], rgb[2])
}
