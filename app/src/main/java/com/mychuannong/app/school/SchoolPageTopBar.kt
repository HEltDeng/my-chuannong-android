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
    val c=AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(title={Text(title,color=c.titleTopColor,fontWeight=FontWeight.Bold,maxLines=1)},
        navigationIcon={TextButton(onClick=onBack){Image(painterResource(R.drawable.ic_back_blue),"返回",Modifier.size(20.dp))}},
        actions={
            IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,"刷新",tint=c.titleTopColor)}
            if(actions.isNotEmpty()) {
                IconButton(onClick={expanded=true}){Icon(Icons.Default.MoreVert,"更多",tint=c.titleTopColor)}
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    actions.forEach{(label,action)->DropdownMenuItem(text={Text(label)},onClick={expanded=false;action()})}
                }
            }
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=c.bgTopBarColor,titleContentColor=c.titleTopColor,navigationIconContentColor=c.titleTopColor))
}

fun openOfficialBrowser(context:android.content.Context,url:String) {
    if(!com.mychuannong.app.SafeUrl.school(url))return
    runCatching{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(android.content.Intent.CATEGORY_BROWSABLE))}
        .onFailure{android.widget.Toast.makeText(context,"未找到浏览器",android.widget.Toast.LENGTH_SHORT).show()}
}
