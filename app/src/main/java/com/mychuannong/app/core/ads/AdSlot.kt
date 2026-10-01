package com.mychuannong.app.core.ads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.theme.AppTheme

/** SDK-neutral renderer contract for a single advertising placement. */
interface AdSlotRenderer {
    @Composable
    fun Render(placement: AdPlacement, modifier: Modifier)
}

/** Default renderer. It deliberately emits no UI and has no side effects. */
object NoOpAdSlotRenderer : AdSlotRenderer {
    @Composable
    override fun Render(placement: AdPlacement, modifier: Modifier) = Unit
}

/**
 * Development placeholder used only when ADS_ENABLED is explicitly enabled.
 * A real provider can replace this renderer later without coupling features to its SDK.
 */
object PlaceholderAdSlotRenderer : AdSlotRenderer {
    @Composable
    override fun Render(placement: AdPlacement, modifier: Modifier) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = AppTheme.colors.bgSecondaryColor
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "广告位预留",
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = placement.stableId,
                    color = AppTheme.colors.greyTextColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Feature-facing advertising entry point. The default build is a true no-op.
 */
@Composable
fun AdSlot(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    renderer: AdSlotRenderer = if (BuildConfig.ADS_ENABLED) {
        PlaceholderAdSlotRenderer
    } else {
        NoOpAdSlotRenderer
    }
) {
    renderer.Render(placement, modifier)
}
