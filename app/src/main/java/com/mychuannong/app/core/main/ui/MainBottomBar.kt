package com.mychuannong.app.core.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.core.main.navigation.MainTabDestination
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SkinColors
import com.mychuannong.app.widget.view.FloatingTabBar
import com.mychuannong.app.widget.view.FloatingTabBarDefaults
import com.mychuannong.app.widget.view.FloatingTabBarScrollConnection
import com.mychuannong.app.widget.view.rememberFloatingTabBarScrollConnection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MainBottomBar(
    selectedDestination: MainTabDestination,
    selectionPosition: Float,
    onDestinationSelected: (MainTabDestination) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val selectedKey = selectedDestination.index
    val colors = AppTheme.colors
    val resources = LocalResources.current
    val currentColors by rememberUpdatedState(colors)
    val currentSelectionPosition by rememberUpdatedState(selectionPosition)
    val currentOnDestinationSelected by rememberUpdatedState(onDestinationSelected)
    val itemContentDescriptions = remember(resources) {
        bottomBarItems.associate { item ->
            item.destination.index to resources.getString(item.destination.labelResId)
        }
    }
    val hazeModifier = remember(hazeState) {
        Modifier.hazeEffect(hazeState) { noiseFactor = 0f }
    }

    FloatingTabBar(
        selectedTabKey = selectedKey,
        selectedTabPosition = selectionPosition,
        scrollConnection = scrollConnection,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        tabBarContentModifier = hazeModifier,
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = colors.bottomBarGlassColor,
            accessoryBackgroundColor = colors.bottomBarGlassColor,
            touchGlowColor = colors.commonColor,
            edgeGlowColor = Color.Transparent
        ),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(0.dp),
            tabInlineContentPadding = PaddingValues(10.dp),
            tabExpandedContentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp),
            tabSpacing = 0.dp
        )
    ) {
        bottomBarItems.forEach { item ->
            tab(
                key = item.destination.index,
                indication = null,
                contentDescription = itemContentDescriptions.getValue(item.destination.index),
                title = {
                    val selectionFraction = selectionFraction(
                        itemIndex = item.destination.index,
                        selectionPosition = currentSelectionPosition,
                    )
                    Text(
                        text = stringResource(item.destination.labelResId),
                        style = bottomBarLabelStyle(selectionFraction),
                        color = bottomBarTint(selectionFraction, currentColors)
                    )
                },
                icon = {
                    val selectionFraction = selectionFraction(
                        itemIndex = item.destination.index,
                        selectionPosition = currentSelectionPosition,
                    )
                    Box(modifier = Modifier.size(BottomBarIconSize)) {
                        Icon(
                            painter = painterResource(item.unselectedIconResId),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = 1f - selectionFraction }
                        )
                        Icon(
                            painter = painterResource(item.selectedIconResId),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = selectionFraction }
                        )
                    }
                },
                onClick = {
                    currentOnDestinationSelected(item.destination)
                }
            )
        }
    }
}

private fun selectionFraction(itemIndex: Int, selectionPosition: Float): Float =
    (1f - abs(itemIndex - selectionPosition)).coerceIn(0f, 1f)

private fun bottomBarTint(selectionFraction: Float, colors: SkinColors): Color =
    lerp(colors.iconSecondaryColor, colors.iconPrimaryColor, selectionFraction)

private fun bottomBarLabelStyle(selectionFraction: Float): TextStyle =
    TextStyle(
        fontSize = BottomBarLabelSize,
        fontWeight = FontWeight(
            (
                FontWeight.Medium.weight +
                    (FontWeight.SemiBold.weight - FontWeight.Medium.weight) * selectionFraction
                ).roundToInt()
        )
    )

private val BottomBarIconSize = 26.dp
private val BottomBarLabelSize = 13.sp

// Keep this list explicit instead of deriving it from MainTabDestination.ordered during
// top-level initialization. Both properties are initialized very early in app startup; deriving
// one from the other can observe a partially initialized data-object list on some runtimes.
private val bottomBarItems = listOf(
    MainBottomBarItem(
        destination = MainTabDestination.Overview,
        selectedIconResId = R.drawable.ic_home,
        unselectedIconResId = R.drawable.ic_unselect_home
    ),
    MainBottomBarItem(
        destination = MainTabDestination.Feature,
        selectedIconResId = R.drawable.ic_service,
        unselectedIconResId = R.drawable.ic_unselect_service
    ),
    MainBottomBarItem(
        destination = MainTabDestination.Profile,
        selectedIconResId = R.drawable.ic_profile,
        unselectedIconResId = R.drawable.ic_unselect_profile
    )
)

private data class MainBottomBarItem(
    val destination: MainTabDestination,
    val selectedIconResId: Int,
    val unselectedIconResId: Int
)

@Preview(showBackground = true)
@Composable
private fun MainBottomBarPreview() {
    AppSkinTheme {
        MainBottomBar(
            selectedDestination = MainTabDestination.Overview,
            selectionPosition = MainTabDestination.Overview.index.toFloat(),
            onDestinationSelected = {},
            scrollConnection = rememberFloatingTabBarScrollConnection(),
            hazeState = rememberHazeState()
        )
    }
}
