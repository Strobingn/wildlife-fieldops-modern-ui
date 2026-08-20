package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun AIHubScreen(
    onOpenChat: () -> Unit = {},
    onOpenOperations: (String?) -> Unit = {},
    onOpenFeature: (String) -> Unit = {},
    onOpenWalkTalk: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    FieldDeskScreen(
        onOpenDrawer = onOpenDrawer,
        onOpenWalkTalk = onOpenWalkTalk
    )
}
