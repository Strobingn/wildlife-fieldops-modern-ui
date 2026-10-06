package com.strobingn.wildlifefieldops.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel

/**
 * The only place business name, phone, email, address, website, NWCO license,
 * and logo are edited for customer documents.
 */
@Composable
fun BusinessInfoSettingsBlock(viewModel: SettingsViewModel) {
    val profile by viewModel.businessProfile.collectAsState()
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setBusinessLogo(uri)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.flushPendingBusinessEdits()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingBusinessEdits()
        }
    }
    val preview = remember(profile.logoPath) {
        profile.logoPath.takeIf { it.isNotBlank() }?.let { path ->
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    Text(
        "Business info",
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryGreen,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Text(
                    "Prints on every estimate, invoice, receipt, inspection, contract, warranty, exclusion, and DEC PDF. Clear a field to leave it off. Reset logo restores the Wildlife Whisperer mark.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                SettingPlainField(
                    storedValue = profile.name,
                    label = "Business name",
                    onCommit = viewModel::setCompanyName
                )
                SettingPlainField(
                    storedValue = profile.phone,
                    label = "Phone",
                    keyboardType = KeyboardType.Phone,
                    onCommit = viewModel::setBusinessPhone
                )
                SettingPlainField(
                    storedValue = profile.email,
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    onCommit = viewModel::setBusinessEmail
                )
                SettingPlainField(
                    storedValue = profile.address,
                    label = "Address",
                    onCommit = viewModel::setCompanyAddress,
                    singleLine = false
                )
                SettingPlainField(
                    storedValue = profile.website,
                    label = "Website",
                    keyboardType = KeyboardType.Uri,
                    onCommit = viewModel::setBusinessWebsite
                )
                SettingPlainField(
                    storedValue = profile.licenseNumber,
                    label = "NYS DEC NWCO license number",
                    onCommit = viewModel::setNwcoLicense
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "Business logo",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            "Wildlife Whisperer logo",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { pickLogo.launch("image/*") }) {
                            Text("Choose logo from gallery")
                        }
                        OutlinedButton(onClick = viewModel::resetBusinessLogo) {
                            Text("Reset to Wildlife Whisperer logo")
                        }
                    }
                }
        }
    }
}
