package com.mychuannong.app.core.theme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
data class SkinColors(val bgCardColor:Color=Color.White,val outlineLowContrastColor:Color=Color(0xFFE2E8ED),val primaryTextColor:Color=Color(0xFF253342),val secondaryTextColor:Color=Color(0xFF677484),val commonColor:Color=Color(0xFF257C71))
object AppTheme { val colors=SkinColors() }
@Composable fun AppSkinTheme(content:@Composable ()->Unit) { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF257C71),background=Color(0xFFF5F7F8)),content=content) }
