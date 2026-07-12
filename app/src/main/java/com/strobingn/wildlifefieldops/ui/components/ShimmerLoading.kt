package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ui.theme.SurfaceDark

/**
 * Shimmer loading effect — shows animated gradient sweep over placeholder shapes.
 * Uses the modern surface color tokens.
 */
@Composable
fun ShimmerBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val base = if (colorScheme.background.luminance() > 0.5f) Color(0xFFe4e4e7) else SurfaceDark
    val shimmerColors = listOf(
        base.copy(alpha = 0.6f),
        base.copy(alpha = 0.3f),
        base.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    val brush = ShimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
    )
}

@Composable
fun ShimmerLine(modifier: Modifier = Modifier, widthFraction: Float = 1f) {
    val brush = ShimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

@Composable
fun ShimmerCircle(modifier: Modifier = Modifier, size: Int = 48) {
    val brush = ShimmerBrush()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(brush)
    )
}

/**
 * Full shimmer layout for the dashboard loading state.
 */
@Composable
fun DashboardShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero shimmer
        ShimmerCard(modifier = Modifier.height(160.dp))

        // Stats row shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerCard(modifier = Modifier.weight(1f).height(96.dp))
            ShimmerCard(modifier = Modifier.weight(1f).height(96.dp))
        }

        // Overview shimmer
        ShimmerCard(modifier = Modifier.height(180.dp))

        // Section title
        ShimmerLine(widthFraction = 0.4f)

        // Job cards shimmer
        repeat(3) {
            ShimmerCard(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Shimmer layout for a list of items.
 */
@Composable
fun ListShimmer(itemCount: Int = 6, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(itemCount) {
            ShimmerCard(modifier = Modifier.height(88.dp))
        }
    }
}
