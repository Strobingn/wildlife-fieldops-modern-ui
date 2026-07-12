package com.strobingn.wildlifefieldops.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Modern shape system with generous rounding for a friendlier, card-heavy UI.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Component-specific tokens
val ShapeCard = RoundedCornerShape(20.dp)
val ShapeCardSmall = RoundedCornerShape(16.dp)
val ShapeButton = RoundedCornerShape(14.dp)
val ShapeInput = RoundedCornerShape(14.dp)
val ShapeChip = RoundedCornerShape(24.dp)
val ShapePill = RoundedCornerShape(percent = 50)
