package com.xperience.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAppColors

private val ExplicitBadgeShape = ContinuousRoundedRectangle(size = 4.dp)
private val ExplicitBadgeCompactShape = ContinuousRoundedRectangle(size = 3.5.dp)

/**
 * Adaptive Apple Music / YouTube Music style "E" badge for explicit tracks.
 *
 * Adapts automatically to dark/light themes via [LocalAppColors], or derives contrast from
 * [tint] when rendered over custom surfaces (e.g. dark artwork scrims or glass chrome).
 * Place inside a `Row` after a title `Text` with `Modifier.weight(1f, fill = false)` so
 * long titles ellipsize cleanly without clipping the badge.
 */
@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    compact: Boolean = false
) {
    val appColors = LocalAppColors.current
    val isDarkContext = if (tint != null) {
        tint.luminance() >= 0.5f
    } else {
        appColors.isDark
    }

    val baseInk = tint ?: appColors.textPrimary
    val surfaceBase = tint ?: if (isDarkContext) Color.White else appColors.textPrimary
    val fgColor = baseInk.copy(alpha = if (isDarkContext) 0.90f else 0.80f)
    val bgColor = surfaceBase.copy(alpha = if (isDarkContext) 0.16f else 0.09f)
    val borderColor = surfaceBase.copy(alpha = if (isDarkContext) 0.28f else 0.20f)

    val shape = if (compact) ExplicitBadgeCompactShape else ExplicitBadgeShape
    val boxSize = if (compact) 14.dp else 16.dp
    val fontSize = if (compact) 8.5.sp else 9.5.sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = "Explicit" }
            .size(boxSize)
            .clip(shape)
            .background(bgColor)
            .border(width = 0.75.dp, color = borderColor, shape = shape)
    ) {
        Text(
            text = "E",
            color = fgColor,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
