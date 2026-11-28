package com.xperience.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.R
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.isCompactWidth
import com.xperience.player.ui.cast.CastButton
import com.xperience.player.ui.cast.CastButtonStyle
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
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

private val TopRightPillShape = ContinuousRoundedRectangle(percent = 50)

@Composable
fun XperienceAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    accentHaloAlpha: Float = 0f,
    accentColor: Color = LocalAccentColor.current
) {
    val appColors = LocalAppColors.current
    val baseBorder = if (appColors.isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }
    val borderColor = if (accentHaloAlpha > 0.02f) {
        accentColor.copy(alpha = (0.35f + accentHaloAlpha * 0.55f).coerceIn(0f, 0.95f))
    } else {
        baseBorder
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (accentHaloAlpha > 0.05f) 10.dp else 6.dp,
                shape = CircleShape,
                ambientColor = if (accentHaloAlpha > 0.05f) accentColor.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.30f),
                spotColor = if (accentHaloAlpha > 0.05f) accentColor.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.36f)
            )
            .clip(CircleShape)
            .background(Color(0xFF0A0F1F))
            .border(
                width = if (accentHaloAlpha > 0.05f) 1.4.dp else 0.9.dp,
                color = borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Xperience Player",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.38f
                    scaleY = 1.38f
                }
        )
    }
}

@Composable
fun HomeTopRightCastSettingsPill(
    castState: CastPlaybackState,
    castRouteCount: Int,
    onCastClick: () -> Unit,
    onSettingsClick: () -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val settingsInteraction = remember { MutableInteractionSource() }

    val surfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = TopRightPillShape,
            highlightAlpha = 0.34f
        )
    } else {
        Modifier
            .background(
                if (appColors.isDark) Color(0xCC161922) else Color(0xEBFFFFFF)
            )
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.09f),
                shape = TopRightPillShape
            )
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(TopRightPillShape)
            .then(surfaceModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CastButton(
                castState = castState,
                onClick = onCastClick,
                availableRouteCount = castRouteCount,
                style = CastButtonStyle.ICON,
                iconSize = 20.dp,
                appColors = appColors,
                modifier = Modifier.size(width = 40.dp, height = 40.dp)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(appColors.glassTextPrimary.copy(alpha = 0.16f))
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressWobble(settingsInteraction)
                    .size(width = 40.dp, height = 40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = settingsInteraction,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = onSettingsClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HeaderBar(
    modifier: Modifier = Modifier,
    showBrandBadge: Boolean = true,
    pullProgress: Float = 0f,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val windowSizeClass = LocalAppWindowSizeClass.current
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

    val clampedPull = pullProgress.coerceIn(0f, 1.4f)
    val animatedPullScale by animateFloatAsState(
        targetValue = if (isRefreshing) 1.08f else 1f + clampedPull * 0.24f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "headerIconPullScale"
    )
    var refreshPulseScale = 1f
    var refreshRotation = 0f
    if (isRefreshing) {
        val refreshTransition = rememberInfiniteTransition(label = "headerRefreshTransition")
        refreshPulseScale = refreshTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "headerRefreshPulse"
        ).value
        refreshRotation = refreshTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "headerRefreshRotation"
        ).value
    }
    val effectiveIconScale = if (isRefreshing) {
        animatedPullScale * refreshPulseScale
    } else {
        animatedPullScale * notePopScale.value
    }
    val effectiveIconRotation = if (isRefreshing) {
        refreshRotation
    } else {
        noteWiggle.value + clampedPull * 24f
    }
    val accentHaloAlpha = if (isRefreshing) 1f else clampedPull.coerceIn(0f, 1f)

    LaunchedEffect(badgeTapCount) {
        if (badgeTapCount > 0) {
            launch {
                notePopScale.snapTo(1.22f)
                notePopScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f)
                )
            }
            launch {
                val dir = if (badgeTapCount % 2 == 0) 1f else -1f
                noteWiggle.snapTo(dir * 12f)
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
            // Top status row with centered app icon (phone) or icon + brand title (tablet/wide)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                // Show brand text alongside icon on non-compact screens when width is comfortable
                val showBrandTitle = !windowSizeClass.isCompactWidth && maxWidth >= 380.dp

                if (showBrandTitle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = badgeJelly.scaleX.value * (if (isRefreshing) 1.03f else 1f + clampedPull * 0.08f)
                                scaleY = badgeJelly.scaleY.value * (if (isRefreshing) 1.03f else 1f + clampedPull * 0.08f)
                                rotationZ = badgeJelly.rotationZ.value
                            }
                            .clip(TopRightPillShape)
                            .background(if (appColors.isDark) Color(0xFF141518) else Color(0xFFFFFFFF))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    if (appColors.isDark)
                                        listOf(Color(0x33FFFFFF), Color(0x10FFFFFF))
                                    else
                                        listOf(Color(0x22000000), Color(0x0A000000))
                                ),
                                shape = TopRightPillShape
                            )
                            .clickable(
                                interactionSource = badgeInteraction,
                                indication = null,
                                onClick = { badgeTapCount++ }
                            )
                            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            XperienceAppIcon(
                                size = 28.dp,
                                accentHaloAlpha = accentHaloAlpha,
                                accentColor = accentColor,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = effectiveIconScale
                                    scaleY = effectiveIconScale
                                    rotationZ = effectiveIconRotation
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Xperience Player",
                                color = appColors.textPrimary,
                                fontFamily = GoogleSans,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    XperienceAppIcon(
                        size = 42.dp,
                        accentHaloAlpha = accentHaloAlpha,
                        accentColor = accentColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = badgeJelly.scaleX.value * effectiveIconScale
                                scaleY = badgeJelly.scaleY.value * effectiveIconScale
                                rotationZ = badgeJelly.rotationZ.value + effectiveIconRotation
                            }
                            .clickable(
                                interactionSource = badgeInteraction,
                                indication = null,
                                onClick = { badgeTapCount++ }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
