package com.hilight.studio

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded()
        }
        val store = Store.get(this)
        setContent {
            val dynamic by store.dynamicColor.collectAsStateWithLifecycle()
            HiLightTheme(dynamicColor = dynamic) {
                App(store, startInSetup = intent?.action == OPEN_SETUP_ACTION)
            }
        }
    }

    /** Without this the app's own notifications are dropped, including the Setup self test. */
    private fun requestNotificationPermissionIfNeeded() {
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Store.get(this).apply {
            syncForegroundWatcher()
            refreshStatus()
        }
    }

    /** A test the user started by hand must not outlive the screen they started it from. */
    override fun onStop() {
        super.onStop()
        Store.get(this).stopPreview()
    }
}

private enum class Tab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    @StringRes val subtitleRes: Int,
) {
    LIVE(R.string.tab_live, Icons.Rounded.Lightbulb, R.string.tab_subtitle_live),
    AMBIENT(R.string.tab_style, Icons.Rounded.Tune, R.string.tab_subtitle_style),
    APPS(R.string.tab_apps, Icons.Rounded.Apps, R.string.tab_subtitle_apps),
    SETUP(R.string.tab_setup, Icons.Rounded.DisplaySettings, R.string.tab_subtitle_setup),
}

@Composable
private fun TopBarStatusPill(
    store: Store,
    onClick: () -> Unit = {},
) {
    val status by store.status.collectAsStateWithLifecycle()
    val active by store.activeTransport.collectAsStateWithLifecycle()
    val rendererConnected = store.isRendererConnectedForUi(status)
    val emeraldDot = Color(0xFF34D399)
    val amberDot = Color(0xFFFBBF24)
    val bg by animateColorAsState(
        if (rendererConnected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        label = "statusPillBg",
    )
    val fg by animateColorAsState(
        if (rendererConnected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onErrorContainer,
        label = "statusPillFg",
    )
    val borderColor by animateColorAsState(
        if (rendererConnected) emeraldDot.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.42f),
        label = "statusPillBorder",
    )
    val dotColor = if (rendererConnected) emeraldDot else amberDot

    Row(
        modifier = Modifier
            .padding(end = 16.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .then(
                    if (rendererConnected) {
                        Modifier.drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        emeraldDot.copy(alpha = 0.50f),
                                        emeraldDot.copy(alpha = 0.15f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = size.minDimension * 0.85f,
                                ),
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            BreathingDot(dotColor, animate = rendererConnected, size = 8)
        }
        Text(
            text = if (rendererConnected) {
                stringResource(
                    R.string.main_connected_pill,
                    status.ledCount,
                    stringResource(active.labelRes),
                )
            } else {
                stringResource(R.string.main_not_connected)
            },
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = fg.copy(alpha = 0.78f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(store: Store, startInSetup: Boolean = false) {
    // saved, so a rotation or a recreated activity does not drop the user back on Live
    var tabIndex by rememberSaveable { mutableIntStateOf(if (startInSetup) Tab.SETUP.ordinal else 0) }
    val tab = Tab.entries[tabIndex.coerceIn(0, Tab.entries.lastIndex)]
    val view = androidx.compose.ui.platform.LocalView.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Tied to the lifecycle, not just the composition: a plain LaunchedEffect keeps its coroutine
    // running once the activity stops, so this polled the helper over binder and file I/O every 1.5s
    // in the background, for a screen nobody was looking at.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                store.refreshStatus()
                delay(1500)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val containerColor = MaterialTheme.colorScheme.primaryContainer
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.28f),
                                            containerColor.copy(alpha = 0.65f),
                                        ),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = primaryColor.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            HiLightAppIcon(size = 34)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AnimatedContent(
                                targetState = tab.subtitleRes,
                                transitionSpec = {
                                    fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                                },
                                label = "tabSubtitle",
                            ) { sub ->
                                Text(
                                    text = stringResource(sub),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    TopBarStatusPill(store) {
                        if (tab != Tab.SETUP) PixelHaptics.click(view)
                        tabIndex = Tab.SETUP.ordinal
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tab.entries.forEach { t ->
                            val selected = tab == t
                            val pillBg by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "dockPillBg",
                            )
                            val pillFg by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "dockPillFg",
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.1f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "dockIconScale",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(pillBg, CircleShape)
                                    .selectable(selected = selected, role = Role.Tab) {
                                        if (tab != t) PixelHaptics.tick(view)
                                        tabIndex = t.ordinal
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = t.icon,
                                        contentDescription = null,
                                        tint = pillFg,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .scale(iconScale),
                                    )
                                    Text(
                                        text = stringResource(t.labelRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = pillFg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        // tabs slide in the direction of travel, like the system's pagers
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { w -> dir * w / 8 } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(320)) { w -> -dir * w / 8 } + fadeOut(tween(160))
                    )
            },
            label = "tab",
            modifier = Modifier.padding(pad),
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (current) {
                    Tab.LIVE -> LiveScreen(store)
                    Tab.AMBIENT -> AmbientScreen(store)
                    Tab.APPS -> AppRulesScreen(store)
                    Tab.SETUP -> SetupScreen(store)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
