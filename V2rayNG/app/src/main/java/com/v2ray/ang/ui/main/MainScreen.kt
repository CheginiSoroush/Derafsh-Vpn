package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.DerafshSourceManager
import com.v2ray.ang.handler.MaintenanceStatus
import com.v2ray.ang.ui.compose.BannerAd
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Derafsh one-tap connect screen. A big connect button with ambient animations (breathing glow
 * while idle, radar rings while connecting, rotating glow while connected) and staggered
 * entrance animations.
 * All operations are dispatched through [MainAction]; navigation through [MainDestination].
 */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isStartPending by mainViewModel.isStartPending.collectAsStateWithLifecycle()
    val connectPhase by mainViewModel.connectPhase.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val allServers by mainViewModel.allServerRows.collectAsStateWithLifecycle()
    val pickerServers by mainViewModel.pickerServerRows.collectAsStateWithLifecycle()
    val showStaleProfiles by mainViewModel.showStaleProfiles.collectAsStateWithLifecycle()
    val derafshActiveSource by mainViewModel.derafshActiveSource.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val appContext = context.applicationContext

    // Maintenance (":tasks" process) publishes the subscription refresh + speed-test
    // progress through the multi-process MMKV store; poll it with a cheap ticker while
    // the screen is visible so both background jobs are announced live.
    var maintenance by remember { mutableStateOf(MaintenanceStatus.read()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            maintenance = MaintenanceStatus.read()
            delay(700)
        }
    }

    // Respect the system "remove animations" accessibility setting: no ambient loops and
    // entrance transitions land instantly.
    val reducedMotion = remember {
        Settings.Global.getFloat(
            appContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }

    var showServerPicker by rememberSaveable { mutableStateOf(false) }
    var showDerafshSources by rememberSaveable { mutableStateOf(false) }

    val selectedRow = remember(allServers, uiState.selectedGuid) {
        uiState.selectedGuid?.let { guid -> allServers.firstOrNull { it.guid == guid } }
    }

    // Staggered entrance animation, played once per composition.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        entered = true
    }
    val topAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 400),
        label = "entranceTopAlpha",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "entranceButtonScale",
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 300),
        label = "entranceButtonAlpha",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450, delayMillis = 220),
        label = "entranceCardAlpha",
    )
    val cardOffset by animateDpAsState(
        targetValue = if (entered) 0.dp else 28.dp,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 450, delayMillis = 220, easing = FastOutSlowInEasing)
        },
        label = "entranceCardOffset",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.alpha(topAlpha)) {
            MainTopRow(
                isLoading = isLoading,
                onSourcesClick = { showDerafshSources = true },
                onSettingsClick = { onNavigate(MainDestination.Settings) },
                onAboutClick = { onNavigate(MainDestination.About) },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                        alpha = buttonAlpha
                    },
                ) {
                    ConnectAmbientVisuals(
                        isRunning = uiState.isRunning,
                        isStartPending = isStartPending,
                        reducedMotion = reducedMotion,
                    )
                    ConnectCircleButton(
                        isRunning = uiState.isRunning,
                        isStartPending = isStartPending,
                        reducedMotion = reducedMotion,
                        onToggle = { onAction(MainAction.ToggleService) },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Derafsh Kaviani: the status label tracks the real connect pipeline.
                AnimatedStatusLabel(
                    isRunning = uiState.isRunning,
                    isStartPending = isStartPending,
                    connectPhase = connectPhase,
                )
            }
        }

        // Derafsh status section 1: background maintenance (sub refresh + speed test).
        MaintenanceCard(
            status = maintenance,
            reducedMotion = reducedMotion,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
        )

        // Derafsh monetization: the banner sits directly beside the maintenance/test card —
        // the startup pass is the longest on-screen dwell time in the app, so the slot has
        // maximum viewable exposure without ever blocking a button.
        BannerAd(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
        )

        SelectedServerCard(
            selectedRow = selectedRow,
            onClick = { showServerPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardOffset.toPx()
                },
        )
    }

    if (showServerPicker) {
        DerafshServerPickerDialog(
            servers = pickerServers,
            selectedGuid = uiState.selectedGuid,
            showStale = showStaleProfiles,
            onToggleShowStale = { onAction(MainAction.SetShowStale(it)) },
            onSelect = { guid ->
                showServerPicker = false
                onAction(MainAction.SelectServer(guid))
            },
            onDismiss = { showServerPicker = false },
        )
    }

    if (showDerafshSources) {
        DerafshSourcesDialog(
            activeIndex = derafshActiveSource,
            onSelect = { index ->
                showDerafshSources = false
                onAction(MainAction.SwitchDerafshSource(index))
            },
            onDismiss = { showDerafshSources = false },
        )
    }

}

@Composable
private fun MainTopRow(
    isLoading: Boolean,
    onSourcesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand: crimson tile with the shield mark + wordmark.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_derafsh_emblem),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_header_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Subscription updates and real-delay tests run automatically (every 4h + on
            // launch), so the header keeps the source switcher, About and Settings — no
            // manual update buttons.
            HeaderActionButton(
                iconRes = R.drawable.ic_subscriptions_24dp,
                description = stringResource(R.string.derafsh_sources_title),
                onClick = onSourcesClick,
            )
            Spacer(modifier = Modifier.width(6.dp))
            HeaderActionButton(
                iconRes = R.drawable.ic_about_24dp,
                description = stringResource(R.string.menu_about),
                onClick = onAboutClick,
            )
            Spacer(modifier = Modifier.width(6.dp))
            HeaderActionButton(
                iconRes = R.drawable.ic_settings_24dp,
                description = stringResource(R.string.title_settings),
                onClick = onSettingsClick,
            )
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** Circular tonal header button with an explicit tint so the action is always readable. */
@Composable
private fun HeaderActionButton(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ConnectCircleButton(
    isRunning: Boolean,
    isStartPending: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "connectButtonScale",
    )
    // Kaviani raise: when the standard comes up, the mark settles into place with a
    // short spring lift. Skipped entirely under reduced motion.
    val raiseScale = remember { Animatable(1f) }
    LaunchedEffect(isRunning, reducedMotion) {
        if (isRunning && !reducedMotion) {
            raiseScale.snapTo(0.9f)
            raiseScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            raiseScale.snapTo(1f)
        }
    }
    val containerColor by animateColorAsState(
        targetValue = when {
            isStartPending -> MaterialTheme.colorScheme.primary
            isRunning -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MILLIS),
        label = "connectButtonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isStartPending -> MaterialTheme.colorScheme.onPrimary
            isRunning -> MaterialTheme.colorScheme.onTertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MILLIS),
        label = "connectButtonContent",
    )
    // Name at the row level so the merged node has one spoken label; the icon is decorative.
    val accessibleName = stringResource(if (isRunning) R.string.acc_stop else R.string.acc_start)

    Box(
        modifier = Modifier
            .size(BUTTON_SIZE.dp)
            .scale(scale * raiseScale.value)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isStartPending,
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics { contentDescription = accessibleName },
        contentAlignment = Alignment.Center,
    ) {
        if (isStartPending) {
            CircularProgressIndicator(
                modifier = Modifier.size(PROGRESS_SIZE.dp),
                color = contentColor,
            )
        } else {
            // The Kaviani standard is the identity of the control — idle it rests,
            // connected it is raised in gold.
            Icon(
                painter = painterResource(R.drawable.ic_derafsh_emblem),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ICON_SIZE.dp),
            )
        }
    }
}

/**
 * Ambient animation layer drawn around the connect button:
 * - idle: breathing brand glow
 * - connecting: expanding radar rings
 * - connected: soft gold glow
 * Under reduced motion every loop is replaced by a single static halo.
 */
@Composable
private fun ConnectAmbientVisuals(
    isRunning: Boolean,
    isStartPending: Boolean,
    reducedMotion: Boolean,
) {
    when {
        isStartPending -> {
            if (reducedMotion) {
                StaticHalo(color = MaterialTheme.colorScheme.primary)
            } else {
                RadarRings(color = MaterialTheme.colorScheme.primary)
            }
        }
        isRunning -> {
            if (!reducedMotion) ConnectedGlow()
        }
        else -> {
            if (reducedMotion) {
                StaticHalo(color = MaterialTheme.colorScheme.primary)
            } else {
                BreathingGlow(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Static, zero-animation halo used when the user asks the system to reduce motion. */
@Composable
private fun StaticHalo(color: Color) {
    Canvas(modifier = Modifier.size((BUTTON_SIZE + RING_PADDING * 2).dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.16f), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f,
            ),
            radius = size.minDimension / 2f,
        )
    }
}

/** Soft pulsing radial glow behind the idle connect button. */
@Composable
private fun BreathingGlow(color: Color) {
    val transition = rememberInfiniteTransition(label = "breathingGlow")
    val glowScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    val brush = Brush.radialGradient(
        colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
    )
    Canvas(
        modifier = Modifier
            .size((BUTTON_SIZE + RING_PADDING * 2).dp)
            .graphicsLayer {
                scaleX = glowScale
                scaleY = glowScale
            },
    ) {
        drawCircle(brush = brush, radius = size.minDimension / 2f)
    }
}

/** Expanding radar rings shown while the fastest-config test is running. */
@Composable
private fun RadarRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "radarRings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
        ),
        label = "radarProgress",
    )
    Canvas(modifier = Modifier.size((BUTTON_SIZE + RING_PADDING * 2).dp)) {
        val maxRadius = size.minDimension / 2f
        val stroke = 2.dp.toPx()
        for (i in 0 until RADAR_RING_COUNT) {
            val phase = (progress + i.toFloat() / RADAR_RING_COUNT) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - phase) * 0.45f),
                radius = maxRadius * (0.45f + 0.55f * phase),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Soft gold halo while connected. */
@Composable
private fun ConnectedGlow() {
    val glowColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size((BUTTON_SIZE + RING_PADDING * 2).dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f,
            ),
            radius = size.minDimension / 2f,
        )
    }
}

@Composable
private fun AnimatedStatusLabel(
    isRunning: Boolean,
    isStartPending: Boolean,
    connectPhase: ConnectPhase,
) {
    val statusRes = when {
        connectPhase == ConnectPhase.TestingServers -> R.string.phase_testing_servers
        isStartPending -> R.string.status_connecting
        isRunning -> R.string.status_connected
        else -> R.string.status_disconnected
    }
    AnimatedContent(
        targetState = statusRes,
        transitionSpec = {
            (fadeIn(animationSpec = tween(250)) + slideInVertically(animationSpec = tween(250)) { it / 2 }) togetherWith
                (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 2 })
        },
        label = "statusLabel",
    ) { res ->
        Text(
            text = stringResource(res),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/**
 * Derafsh maintenance status card: announces the automatic subscription refresh and
 * real-delay speed test that run on app open (4-hour cycle) with a live progress bar.
 * Progress is published by the ":tasks" process and polled via [MaintenanceStatus].
 */
@Composable
private fun MaintenanceCard(
    status: MaintenanceStatus.Snapshot,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // Live clock so the DONE state hides itself after its visibility window.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Loading-screen feel: the percentage and the server counter roll forward smoothly
    // between progress ticks instead of jumping in coarse steps.
    val animatedFraction by animateFloatAsState(
        targetValue = status.fraction,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 500, easing = FastOutSlowInEasing)
        },
        label = "maintAnimatedFraction",
    )
    val visible = when (status.phase) {
        MaintenanceStatus.Phase.UPDATING, MaintenanceStatus.Phase.TESTING -> true
        MaintenanceStatus.Phase.DONE -> status.doneAt > 0L && now - status.doneAt < MAINT_DONE_VISIBLE_MILLIS
        MaintenanceStatus.Phase.IDLE -> false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_24dp),
                        contentDescription = null,
                        tint = if (status.phase == MaintenanceStatus.Phase.DONE) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            when (status.phase) {
                                MaintenanceStatus.Phase.UPDATING -> R.string.maint_title_updating
                                MaintenanceStatus.Phase.TESTING -> R.string.maint_title_testing
                                else -> R.string.maint_title_done
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.phase == MaintenanceStatus.Phase.TESTING && status.total > 0) {
                        Text(
                            text = String.format(
                                Locale.getDefault(), "%d%%", (animatedFraction * 100f).roundToInt()
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                val subtitle = when (status.phase) {
                    MaintenanceStatus.Phase.UPDATING ->
                        if (status.subLabel.isNotBlank()) {
                            stringResource(R.string.maint_updating_from, status.subLabel)
                        } else {
                            ""
                        }
                    MaintenanceStatus.Phase.TESTING ->
                        if (status.total > 0) {
                            stringResource(
                                R.string.maint_progress,
                                (animatedFraction * status.total).roundToInt().coerceAtMost(status.total),
                                status.total,
                            )
                        } else {
                            ""
                        }
                    MaintenanceStatus.Phase.DONE -> stringResource(R.string.maint_done_body)
                    MaintenanceStatus.Phase.IDLE -> ""
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status.phase == MaintenanceStatus.Phase.UPDATING ||
                    status.phase == MaintenanceStatus.Phase.TESTING
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DerafshProgressBar(
                        fraction = if (status.phase == MaintenanceStatus.Phase.TESTING) {
                            status.fraction
                        } else {
                            null
                        },
                        active = true,
                        reducedMotion = reducedMotion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                if (subtitle.isNotBlank()) contentDescription = subtitle
                            },
                    )
                }
            }
        }
    }
}

/**
 * Derafsh brand progress bar: rounded track with a crimson→gold gradient fill and a soft
 * shimmer sweep while active. Pass `fraction = null` for the indeterminate sweeping mode.
 */
@Composable
private fun DerafshProgressBar(
    fraction: Float?,
    active: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillStart = MaterialTheme.colorScheme.primary
    val fillEnd = MaterialTheme.colorScheme.tertiary
    val easedFill by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 350, easing = FastOutSlowInEasing)
        },
        label = "derafshProgressFill",
    )
    val sweep = rememberInfiniteTransition(label = "derafshProgressSweep")
    val sweepPos by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "derafshSweepPos",
    )

    Canvas(modifier = modifier.height(10.dp).clipToBounds()) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor.copy(alpha = 0.65f), cornerRadius = corner)
        if (fraction == null) {
            // Indeterminate: a short brand band sweeps across the whole track.
            val bandWidth = size.width * 0.35f
            val x = (size.width + bandWidth) * sweepPos - bandWidth
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(fillStart, fillEnd)),
                topLeft = Offset(x, 0f),
                size = Size(bandWidth, size.height),
                cornerRadius = corner,
            )
        } else {
            val fillWidth = size.width * easedFill.coerceIn(0f, 1f)
            if (fillWidth > 0.5f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(fillStart, fillEnd)),
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner,
                )
                if (active && !reducedMotion) {
                    // Shimmer highlight gliding inside the filled region.
                    val bandWidth = minOf(fillWidth * 0.45f, size.width * 0.22f)
                    val maxX = (fillWidth - bandWidth).coerceAtLeast(0f)
                    val x = (fillWidth + bandWidth) * sweepPos - bandWidth
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(x.coerceIn(0f, maxX), 0f),
                        size = Size(minOf(bandWidth, fillWidth), size.height),
                        cornerRadius = corner,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedServerCard(
    selectedRow: ServerRowUiModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedRow != null) {
                // Derafsh privacy: only the location flag identifies the server —
                // config names and hostnames never reach the UI.
                Text(
                    text = selectedRow.flag,
                    style = MaterialTheme.typography.displayMedium,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedRow.typeDescription,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ServerDelayText(testDelayMillis = selectedRow.testDelayMillis)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.server_fastest_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.choose_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BUTTON_SIZE = 200
private const val PROGRESS_SIZE = 64
private const val ICON_SIZE = 72
private const val CARD_CORNER_RADIUS = 24
private const val COLOR_ANIMATION_MILLIS = 250
private const val RING_PADDING = 36
private const val RADAR_RING_COUNT = 3
private const val MAINT_DONE_VISIBLE_MILLIS = 7000L
