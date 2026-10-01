package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.MoneyField
import com.strobingn.wildlifefieldops.ui.theme.AccentAmber
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

@Composable
fun pricingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark
)

@Composable
fun DecimalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.filter { c -> c.isDigit() || c == '.' || (allowNegative && c == '-') })
        },
        label = { Text(label) },
        colors = pricingFieldColors(),
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
}

@Composable
fun OverridableAmountField(
    label: String,
    field: MoneyField,
    onOverride: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(Money.format(field.effective)) }

    LaunchedEffect(field.effective, field.isOverridden, focused) {
        if (!focused) {
            text = Money.format(field.effective)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { c -> c.isDigit() || c == '.' || c == '-' }
                text = filtered
                Money.parse(filtered)?.let { onOverride(it) }
            },
            label = { Text(label) },
            leadingIcon = {
                Text(
                    "$",
                    color = if (emphasized) PrimaryGreen else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = pricingFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                color = if (emphasized) PrimaryGreen else TextPrimary
            )
        )
        OverrideCaption(field = field, onReset = onReset)
    }
}

@Composable
fun OverrideCaption(
    field: MoneyField,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!field.isOverridden) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
                color = AccentAmber.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "manual",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val calc = Money.formatUsd(field.calculated)
            val delta = field.difference
            val caption = if (Money.equals(delta, 0.0)) {
                "calc $calc"
            } else {
                "calc $calc  (${Money.formatSignedUsd(delta)})"
            }
            Text(caption, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Undo,
                contentDescription = "Reset to calculated",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun CompactOverridableAmount(
    field: MoneyField,
    onOverride: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(Money.format(field.effective)) }
    LaunchedEffect(field.effective, field.isOverridden, focused) {
        if (!focused) text = Money.format(field.effective)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { c -> c.isDigit() || c == '.' || c == '-' }
                text = filtered
                Money.parse(filtered)?.let { onOverride(it) }
            },
            colors = pricingFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                fontSize = 13.sp
            )
        )
        if (field.isOverridden) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("manual", style = MaterialTheme.typography.labelSmall, color = AccentAmber, fontSize = 9.sp)
                IconButton(onClick = onReset, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Undo, contentDescription = "Reset to calculated", tint = TextSecondary, modifier = Modifier.size(12.dp))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
