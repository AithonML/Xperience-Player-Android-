package com.xperience.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.launch

@Stable
internal class AuroraJellyPressState(
    val scaleX: Animatable<Float, AnimationVector1D>,
    val scaleY: Animatable<Float, AnimationVector1D>,
    val rotationZ: Animatable<Float, AnimationVector1D>
)

@Composable
internal fun rememberAuroraJellyPressState(
    interactionSource: MutableInteractionSource,
    pressedScaleX: Float = 0.955f,
    pressedScaleY: Float = 0.935f,
    releaseVelocityX: Float = 1.65f,
    releaseVelocityY: Float = 0.85f,
    dampingRatio: Float = 0.42f,
    stiffness: Float = 420f,
    releaseWobbleDeg: Float = 0f
): AuroraJellyPressState {
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val rotationZ = remember { Animatable(0f) }

    LaunchedEffect(
        interactionSource,
        pressedScaleX,
        pressedScaleY,
        dampingRatio,
        stiffness,
        releaseWobbleDeg
    ) {
        val pressSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 620f)
        val releaseSpecX = spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness)
        val releaseSpecY = spring<Float>(
            dampingRatio = (dampingRatio + 0.03f).coerceAtMost(0.65f),
            stiffness = stiffness + 30f
        )
        val cancelSpec = spring<Float>(dampingRatio = 0.78f, stiffness = 480f)
        var wobbleDirection = 1f

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    launch { scaleX.animateTo(pressedScaleX, pressSpec) }
                    launch { scaleY.animateTo(pressedScaleY, pressSpec) }
                    if (releaseWobbleDeg != 0f) {
                        launch {
                            rotationZ.animateTo(-wobbleDirection * releaseWobbleDeg * 0.4f, pressSpec)
                        }
                    }
                }
                is PressInteraction.Release -> {
                    launch {
                        scaleX.animateTo(
                            targetValue = 1f,
                            animationSpec = releaseSpecX,
                            initialVelocity = releaseVelocityX
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = 1f,
                            animationSpec = releaseSpecY,
                            initialVelocity = releaseVelocityY
                        )
                    }
                    if (releaseWobbleDeg != 0f) {
                        val dir = wobbleDirection
                        wobbleDirection = -wobbleDirection
                        launch {
                            rotationZ.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.36f, stiffness = 380f),
                                initialVelocity = dir * releaseWobbleDeg * 14f
                            )
                        }
                    }
                }
                is PressInteraction.Cancel -> {
                    launch { scaleX.animateTo(1f, cancelSpec) }
                    launch { scaleY.animateTo(1f, cancelSpec) }
                    if (releaseWobbleDeg != 0f) {
                        launch { rotationZ.animateTo(0f, cancelSpec) }
                    }
                }
            }
        }
    }

    return remember(scaleX, scaleY, rotationZ) {
        AuroraJellyPressState(scaleX = scaleX, scaleY = scaleY, rotationZ = rotationZ)
    }
}

@Composable
fun HeaderBar(
    modifier: Modifier = Modifier,
    showBrandBadge: Boolean = true,
    onRefreshClick: (() -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val badgeInteraction = remember { MutableInteractionSource() }
    val badgeJelly = rememberAuroraJellyPressState(
        interactionSource = badgeInteraction,
        pressedScaleX = 0.94f,
        pressedScaleY = 0.90f,
        releaseVelocityX = 1.9f,
        releaseVelocityY = 1.0f,
        dampingRatio = 0.42f,
        stiffness = 420f,
        releaseWobbleDeg = 2.0f
    )
    var badgeTapCount by remember { mutableIntStateOf(0) }
    val notePopScale = remember { Animatable(1f) }
    val noteWiggle = remember { Animatable(0f) }

    LaunchedEffect(badgeTapCount) {
        if (badgeTapCount > 0) {
            launch {
                notePopScale.snapTo(1.26f)
                notePopScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f)
                )
            }
            launch {
                val dir = if (badgeTapCount % 2 == 0) 1f else -1f
                noteWiggle.snapTo(dir * 14f)
                noteWiggle.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.38f, stiffness = 380f)
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        if (showBrandBadge) {
            // Top status row with centered badge
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // App badge pill centered
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = badgeJelly.scaleX.value
                            scaleY = badgeJelly.scaleY.value
                            rotationZ = badgeJelly.rotationZ.value
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (appColors.isDark) Color(0xFF141518) else Color(0xFFFFFFFF))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                if (appColors.isDark)
                                    listOf(Color(0x33FFFFFF), Color(0x10FFFFFF))
                                else
                                    listOf(Color(0x22000000), Color(0x0A000000))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = badgeInteraction,
                            indication = null,
                            onClick = { badgeTapCount++ }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Coral pink icon square
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    val s = notePopScale.value
                                    scaleX = s
                                    scaleY = s
                                    rotationZ = noteWiggle.value
                                }
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF7272)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Xperience Player",
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large bold "Home" header using Google Sans
            Text(
                text = "Home",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = if (showBrandBadge) 42.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            if (onRefreshClick != null) {
                val refreshInteraction = remember { MutableInteractionSource() }
                val refreshJelly = rememberAuroraJellyPressState(
                    interactionSource = refreshInteraction,
                    pressedScaleX = 0.94f,
                    pressedScaleY = 0.91f
                )
                // HeaderBar renders inside HomeScreen's layerBackdrop(appBackdrop); sampling LocalAppBackdrop
                // here would create a cyclic RenderNode graph in HWUI, so use a static frosted pill surface.
                val pillFill = if (appColors.isDark) {
                    Color(0xB81A1E29)
                } else {
                    Color.White.copy(alpha = 0.84f)
                }
                val pillBorder = if (appColors.isDark) {
                    Color.White.copy(alpha = 0.16f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = refreshJelly.scaleX.value
                            scaleY = refreshJelly.scaleY.value
                        }
                        .clip(CircleShape)
                        .background(pillFill, CircleShape)
                        .border(width = 0.9.dp, color = pillBorder, shape = CircleShape)
                        .clickable(
                            interactionSource = refreshInteraction,
                            indication = null,
                            onClick = onRefreshClick
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Recommendations",
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Refresh Recommendations",
                        color = appColors.textPrimary,
                        fontFamily = PlusJakartaSans,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
