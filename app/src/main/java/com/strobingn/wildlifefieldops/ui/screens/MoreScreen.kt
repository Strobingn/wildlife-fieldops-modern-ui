package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.navigation.FieldNav
import com.strobingn.wildlifefieldops.navigation.MoreDestination
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.components.FieldSearchBar
import com.strobingn.wildlifefieldops.ui.theme.FieldMetrics

@Composable
fun MoreScreen(
    onOpen: (MoreDestination) -> Unit,
    header: @Composable () -> Unit,
    primaryActions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val visible = FieldNav.moreDestinations.filter { dest ->
        needle.isBlank() ||
            dest.label.contains(needle, ignoreCase = true) ||
            dest.blurb.contains(needle, ignoreCase = true) ||
            dest.group.contains(needle, ignoreCase = true) ||
            dest.screen.title.contains(needle, ignoreCase = true)
    }
    val groups = FieldNav.groupsInOrder.map { group ->
        group to visible.filter { it.group == group }
    }.filter { it.second.isNotEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = FieldMetrics.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FieldMetrics.space8)
        ) {
            item {
                header()
                primaryActions()
                Spacer(modifier = Modifier.height(FieldMetrics.space8))
                FieldSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search tools"
                )
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        "No tools match “$needle”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = FieldMetrics.space24)
                    )
                }
            }
            groups.forEach { (group, rows) ->
                item(key = "group-$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = FieldMetrics.space12, bottom = FieldMetrics.space4)
                    )
                }
                items(rows, key = { "${it.group}-${it.label}" }) { dest ->
                    MoreToolRow(dest = dest, onClick = { onOpen(dest) })
                }
            }
            item { Spacer(modifier = Modifier.height(FieldMetrics.space24)) }
        }
    }
}

@Composable
private fun MoreToolRow(dest: MoreDestination, onClick: () -> Unit) {
    FieldCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FieldMetrics.primaryTouch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dest.screen.icon?.let { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(FieldMetrics.space12))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dest.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dest.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
