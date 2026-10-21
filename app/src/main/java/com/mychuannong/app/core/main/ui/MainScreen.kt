package com.mychuannong.app.core.main.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.main.navigation.MainTabDestination
import com.mychuannong.app.core.main.navigation.MainTabNavigator
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.FunctionDestination
import com.mychuannong.app.feature.common.compose_ui.openFunctionShortcut
import com.mychuannong.app.feature.common.compose_ui.primaryFunctionShortcuts
import com.mychuannong.app.feature.common.ui.FeatureScreen
import com.mychuannong.app.overview.ui.compose.OverviewScreen
import com.mychuannong.app.overview.viewmodel.OverviewViewModel
import com.mychuannong.app.profileSettings.ui.compose.ProfileSettingsRoute
import com.mychuannong.app.widget.view.rememberFloatingTabBarScrollConnection
import com.mychuannong.app.widget.view.FloatingTabBarScrollConnection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs

@Composable
fun MainScreen(
    navigator: MainTabNavigator,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scrollConnection = rememberFloatingTabBarScrollConnection()
    val hazeState = rememberHazeState()
    val destinations = MainTabDestination.ordered
    val initialPage = MainTabDestination.indexOf(navigator.currentDestination) ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { destinations.size }
    )
    var userDragStartPage by remember(pagerState) { mutableStateOf<Int?>(null) }
    // Keep the three small native tabs after the first switch. Subsequent switches
    // reuse their composition and scroll state; cold startup still builds only its page.
    var retainTabs by remember(pagerState) { mutableStateOf(false) }

    // Bottom-bar taps and AppEventBus selections update the navigator first. Mirror those changes
    // into the pager with one short, native horizontal animation.
    LaunchedEffect(navigator.currentDestination) {
        val targetPage = MainTabDestination.indexOf(navigator.currentDestination) ?: return@LaunchedEffect
        if (
            pagerState.settledPage != targetPage ||
            pagerState.currentPage != targetPage ||
            abs(pagerState.currentPageOffsetFraction) > 0.001f
        ) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(durationMillis = TabSwitchAnimationDurationMillis)
            )
        }
    }

    // Record only actual touch drags. Programmatic pager animations must not create a second
    // haptic response after the bottom bar has already acknowledged the tap.
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userDragStartPage = pagerState.settledPage
            }
        }
    }

    // Commit the selected tab only once the native pager has settled. A rebound at either edge or
    // a drag that stays on the same page intentionally produces no navigation or haptic feedback.
    LaunchedEffect(pagerState, navigator) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (isScrolling, settledPage) ->
                if (isScrolling) return@collect

                val dragStartPage = userDragStartPage
                userDragStartPage = null
                val destination = destinations.getOrNull(settledPage) ?: return@collect
                if (dragStartPage == null) {
                    // A cancelled programmatic animation can briefly settle on an intermediate
                    // page. The latest navigator request remains authoritative in that case.
                    val requestedPage = MainTabDestination.indexOf(navigator.currentDestination)
                    if (requestedPage != settledPage) return@collect
                } else {
                    navigator.select(destination)
                }
                if (dragStartPage != null && dragStartPage != settledPage) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (settledPage != initialPage) retainTabs = true
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppTheme.colors.bgPrimaryColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                key = { page -> destinations[page].index },
                beyondViewportPageCount = if (retainTabs) destinations.lastIndex else 0,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapPositionalThreshold = 0.5f
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection)
                    .hazeSource(hazeState),
            ) { page ->
                when (destinations[page]) {
                    MainTabDestination.Overview -> OverviewTabRoute()
                    MainTabDestination.Feature -> FeatureScreen(
                        isActive = pagerState.settledPage == page && !pagerState.isScrollInProgress,
                    )
                    MainTabDestination.Profile -> ProfileSettingsRoute(
                        isActive = pagerState.settledPage == page && !pagerState.isScrollInProgress,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                PagerBottomBar(
                    navigator = navigator,
                    pagerState = pagerState,
                    scrollConnection = scrollConnection,
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

private const val TabSwitchAnimationDurationMillis = 220

/** Read per-frame pager offsets only in the bar, not in the whole page host. */
@Composable
private fun PagerBottomBar(
    navigator: MainTabNavigator,
    pagerState: PagerState,
    scrollConnection: FloatingTabBarScrollConnection,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    MainBottomBar(
        selectedDestination = navigator.currentDestination,
        selectionPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, MainTabDestination.ordered.lastIndex.toFloat()),
        onDestinationSelected = navigator::select,
        scrollConnection = scrollConnection,
        hazeState = hazeState,
        modifier = modifier,
    )
}

@Composable
private fun OverviewTabRoute() {
    val context = LocalContext.current
    val viewModel: OverviewViewModel = viewModel()

    OverviewScreen(
        viewModel = viewModel,
        onBindClick = { Route.goBindingUser(context) },
        onQuickActionClick = { actionId ->
            val config = RemoteConfigurationStore.appConfig.value.envelope.config
            val shortcut = primaryFunctionShortcuts(config).firstOrNull { it.id == actionId }
            when {
                shortcut != null -> openFunctionShortcut(context, shortcut)
                actionId == FunctionDestination.ScoreInquiry.name -> Route.goScoreInquiry(context)
                actionId == FunctionDestination.Electronic.name -> Route.goElectronic(context)
            }
        }
    )
}
