package com.strobingn.wildlifefieldops.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Wildlife Field Ops for Windows. v0.1: Supabase sign-in, live job board,
 * notes editing pushed back to the same rows the Android app syncs.
 *
 * Credentials are read from environment variables (never committed):
 *   FIELDOPS_SUPABASE_URL, FIELDOPS_SUPABASE_ANON_KEY
 */
fun main() = application {
    val url = System.getenv("FIELDOPS_SUPABASE_URL").orEmpty()
    val key = System.getenv("FIELDOPS_SUPABASE_ANON_KEY").orEmpty()
    if (url.isBlank() || key.isBlank()) {
        Window(onCloseRequest = ::exitApplication, title = "Wildlife Field Ops") {
            Text("Set FIELDOPS_SUPABASE_URL and FIELDOPS_SUPABASE_ANON_KEY environment variables, then restart.")
        }
        return@application
    }
    Window(onCloseRequest = ::exitApplication, title = "Wildlife Field Ops — Windows") {
        App(DesktopSyncClient(url, key))
    }
}

@Composable
fun App(sync: DesktopSyncClient) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf<List<DesktopSyncClient.RemoteJob>>(emptyList()) }
    var status by remember { mutableStateOf("Ready.") }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        busy = true
        status = "Pulling jobs from Supabase..."
        try {
            jobs = sync.pullJobs().sortedByDescending { it.scheduledStart ?: "" }
            status = "${jobs.size} jobs synced. Last pull: ${now()}"
        } catch (e: Exception) {
            status = "Pull failed: ${e.message}"
        }
        busy = false
    }

    MaterialTheme {
        if (!signedIn) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wildlife Field Ops — sign in", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Signing in..."
                            if (sync.signIn(email.trim(), password)) {
                                signedIn = true
                                refresh()
                            } else {
                                status = "Sign-in failed. Check email/password."
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank()
                ) { Text("Sign in") }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Job Board", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { scope.launch { refresh() } }, enabled = !busy) { Text("Pull now") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            sync.signOut()
                            signedIn = false
                            jobs = emptyList()
                            status = "Signed out."
                        }
                    }) { Text("Sign out") }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (jobs.isEmpty() && !busy) {
                    Text("No jobs yet. Create one on the phone, then Pull now.")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        JobCard(job, onPushNotes = { notes ->
                            scope.launch {
                                busy = true
                                val err = sync.pushJobNotes(job.id, notes)
                                status = err ?: "Notes pushed to job ${job.id.take(8)}... at ${now()}"
                                if (err == null) jobs = sync.pullJobs().sortedByDescending { it.scheduledStart ?: "" }
                                busy = false
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: DesktopSyncClient.RemoteJob, onPushNotes: (String) -> Unit) {
    var draft by remember(job.id) { mutableStateOf(job.notes.orEmpty()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.title.ifBlank { job.customerName }, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    job.status,
                    job.priority,
                    job.species,
                    job.address?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
            if (job.estimate != null && job.estimate > 0) {
                Text(String.format("Estimate: $%.2f", job.estimate), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                draft, { draft = it },
                label = { Text("Notes (syncs to phone)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPushNotes(draft) }, enabled = draft != (job.notes ?: "")) { Text("Push notes") }
            }
        }
    }
}

private fun now(): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.now())
