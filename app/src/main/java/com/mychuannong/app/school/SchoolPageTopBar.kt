package com.mychuannong.app.school
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppTheme

/** ClassInfoActivity's existing navigation drawable, Material control and theme parameters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SchoolPageTopBar(title:String,onBack:()->Unit,onRefresh:()->Unit,actions:List<Pair<String,()->Unit>> = emptyList()) {
    com.mychuannong.app.core.theme.SecondaryScaffoldHeader(title,onBack) {
        com.mychuannong.app.core.web.PageActionsMenu(listOf("刷新" to onRefresh)+actions)
    }
}

fun openOfficialBrowser(context:android.content.Context,url:String) {
    if(!com.mychuannong.app.SafeUrl.school(url))return
    runCatching{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(android.content.Intent.CATEGORY_BROWSABLE))}
        .onFailure{android.widget.Toast.makeText(context,"未找到浏览器",android.widget.Toast.LENGTH_SHORT).show()}
}
