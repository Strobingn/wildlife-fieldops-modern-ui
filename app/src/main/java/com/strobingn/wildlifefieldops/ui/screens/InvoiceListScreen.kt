package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.InvoiceReminder
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.MoneyFieldOpsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit = {},
    viewModel: MoneyFieldOpsViewModel = hiltViewModel()
) {
    val invoices by viewModel.invoices.collectAsState()
    val overdue by viewModel.overdue.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val context = LocalContext.current
    var overdueOnly by remember { mutableStateOf(true) }
    val list = if (overdueOnly) overdue else invoices

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoices", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = overdueOnly, onClick = { overdueOnly = true }, label = { Text("Overdue (${overdue.size})") })
                    FilterChip(selected = !overdueOnly, onClick = { overdueOnly = false }, label = { Text("All (${invoices.size})") })
                }
            }
            items(list, key = { it.id }) { invoice ->
                InvoiceReminderCard(
                    invoice = invoice,
                    onSent = { viewModel.markInvoice(invoice, InvoiceStatus.SENT) },
                    onPaid = { viewModel.markInvoice(invoice, InvoiceStatus.PAID) },
                    onOpenJob = { if (invoice.jobId.isNotBlank()) onOpenJob(invoice.jobId) },
                    onSms = {
                        val draft = InvoiceReminder.draft(invoice)
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                            putExtra("sms_body", draft.body)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onEmail = {
                        val draft = InvoiceReminder.draft(invoice)
                        val mail = invoice.customerEmail.ifBlank { "" }
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                            putExtra(Intent.EXTRA_TEXT, draft.body)
                        }
                        runCatching { context.startActivity(intent) }
                        viewModel.markReminded(invoice)
                    }
                )
            }
        }
    }
}

@Composable
private fun InvoiceReminderCard(
    invoice: Invoice,
    onSent: () -> Unit,
    onPaid: () -> Unit,
    onOpenJob: () -> Unit,
    onSms: () -> Unit,
    onEmail: () -> Unit
) {
    val overdue = InvoiceReminder.isOverdue(invoice)
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                invoice.invoiceNumber.ifBlank { "Invoice" } + " · " + invoice.status.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(invoice.customerName, color = TextSecondary)
            Text(
                "Due ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(invoice.dueDate))} · $${"%.2f".format(invoice.balanceDue.takeIf { it > 0 } ?: invoice.totalAmount)}",
                color = if (overdue) StatusUrgent else TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSent) { Text("Mark sent") }
                Button(
                    onClick = onPaid,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Paid") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSms) { Text("SMS draft") }
                OutlinedButton(onClick = onEmail) { Text("Email draft") }
                OutlinedButton(onClick = onOpenJob) { Text("Job") }
            }
        }
    }
}
