package com.mychuannong.app.school

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.BuildConfig
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mychuannong.app.core.theme.AppBackButton
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Campus places displayed on the consent-gated native AMap. */
class CampusMapActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setComposeContent { CampusExplorer(onBack = ::finish) }
    }
}

private val CampusGreen = Color(0xFF237B62)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusExplorer(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val configured = BuildConfig.AMAP_API_KEY.isNotBlank()
    var accepted by rememberSaveable { mutableStateOf(CampusAmapConsent.agreed(context)) }
    var consent by rememberSaveable { mutableStateOf(configured && !CampusAmapConsent.hasChoice(context)) }
    if (configured && accepted) {
        AmapExplorer(onBack)
    } else {
        val colors = AppTheme.colors
        Box(Modifier.fillMaxSize().background(colors.bgPrimaryColor)
            .windowInsetsPadding(WindowInsets.safeDrawing).testTag("campus-map-unavailable")) {
            Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                AppBackButton(onBack)
                Text("校园地图", fontSize=22.sp, fontWeight=FontWeight.Bold,
                    color=colors.primaryTextColor, modifier=Modifier.padding(start=12.dp))
            }
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Map, null, tint=CampusGreen, modifier=Modifier.size(48.dp))
                Text(if(configured) "使用高德地图查看校园" else "高德地图暂未配置",
                    color=colors.primaryTextColor, modifier=Modifier.padding(top=20.dp))
                Text(if(configured) "启用后可浏览、搜索校园地点" else "此安装包暂时无法加载校园地图",
                    color=colors.primaryTextColor.copy(alpha=.58f), fontSize=13.sp,
                    modifier=Modifier.padding(top=8.dp,bottom=16.dp))
                if(configured) Button(onClick={consent=true}, modifier=Modifier.testTag("campus-amap-enable"),
                    colors=ButtonDefaults.buttonColors(containerColor=CampusGreen)) { Text("启用高德地图") }
            }
        }
    }
    if(consent) AlertDialog(onDismissRequest={ consent=false;CampusAmapConsent.decline(context) },
        containerColor=Color.White,
        tonalElevation=0.dp,
        titleContentColor=Color(0xFF333333),
        textContentColor=Color(0xFF666666),
        title={Text("使用高德校园地图")},
        text={ Column {
            Text("高德地图由北京高德图强科技有限公司提供。联网加载底图与校园地点时，SDK 会处理地图视野、地点查询词、IP、设备与系统参数、OAID（可用时）及传感器等必要信息。本功能不启用手机定位，不读取学校账号或学习数据。")
            TextButton(onClick={uriHandler.openUri(CampusAmapConsent.PRIVACY_URL)}) { Text("查看高德隐私政策") }
        } },
        confirmButton={ TextButton(onClick={
            CampusAmapConsent.accept(context);consent=false;accepted=true
        },modifier=Modifier.testTag("campus-amap-accept")) { Text("同意并使用") } },
        dismissButton={TextButton(onClick={consent=false;CampusAmapConsent.decline(context)},
            modifier=Modifier.testTag("campus-amap-decline")) { Text("暂不使用") }})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmapExplorer(onBack: () -> Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var amap by remember { mutableStateOf<CampusAmapView?>(null) }
    var geoCamera by rememberSaveable { mutableStateOf<FloatArray?>(null) }
    val colors = AppTheme.colors
    val dark = colors.bgPrimaryColor.luminance() < .5f
    val card = colors.bgCardColor
    val foreground = colors.primaryTextColor
    val secondary = foreground.copy(alpha=.58f)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var campusId by rememberSaveable { mutableStateOf(CampusMap.CHENGDU.id) }
    var categoryName by rememberSaveable { mutableStateOf(PlaceCategory.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val campus = CampusMap.entries.first { it.id == campusId }
    val category = PlaceCategory.valueOf(categoryName)
    val searching = query.isNotBlank()
    val repository = remember { CampusAmapData(context) }
    var geoData by remember { mutableStateOf<Map<CampusMap,CampusGeoData>>(emptyMap()) }
    var geoErrors by remember { mutableStateOf<Map<CampusMap,String>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        CampusAmapConsent.initialize(context)
        loading=true
        try {
            for(item in listOf(campus)+CampusMap.entries.filter { it!=campus }) {
                if(item in geoData) continue
                geoErrors=geoErrors-item
                try { geoData=geoData+(item to repository.load(item)) }
                catch(e: CancellationException) { throw e }
                catch(e: Exception) { geoErrors=geoErrors+(item to CampusAmapData.errorText(e)) }
            }
        } finally { loading=false }
    }
    val catalogue = remember(geoData) {
        geoData.flatMap { (campus,data) -> CampusAmapCatalogue.places(campus,data) }
    }
    val categories = remember(catalogue,campus) { PlaceCategory.entries.filter { item ->
        item==PlaceCategory.ALL || catalogue.any { it.campus==campus && it.category==item }
    } }
    val results = remember(campus,category,query,catalogue,selectedId) {
        val matches=CampusPlaces.visible(campus,category,query,catalogue)
        // The collapsed sheet clips the tall list; keep the selected card in its visible first row.
        if(query.isBlank()) matches.sortedBy { it.id!=selectedId } else matches
    }
    val waitingForPlaces=loading && (searching || campus !in geoData)
    val loadError=geoErrors[campus]
    val mapStatus=loadError ?: if(geoData[campus]==null) "正在加载${campus.title}…"
        else catalogue.firstOrNull { it.id==selectedId }?.name ?: "高德地图 · ${catalogue.count { it.campus==campus }} 个地点"
    val listState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val sheet = rememberStandardBottomSheetState(initialValue=SheetValue.PartiallyExpanded,skipHiddenState=true)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState=sheet)
    val selectPlace: (CampusPlace) -> Unit = { place ->
        keyboard?.hide(); focus.clearFocus()
        if(campusId!=place.campus.id) geoCamera=null
        campusId=place.campus.id; categoryName=place.category.name
        selectedId=place.id; query=""; focusRequest++
        scope.launch { sheet.partialExpand() }
    }
    val latestSelect by rememberUpdatedState(selectPlace)
    fun closeInput() { keyboard?.hide();focus.clearFocus() }
    fun switchCampus(value: CampusMap) {
        if(campusId!=value.id) geoCamera=null
        closeInput();campusId=value.id;categoryName=PlaceCategory.ALL.name;query="";selectedId=null
        scope.launch { listState.scrollToItem(0); sheet.partialExpand() }
    }
    BackHandler {
        when {
            query.isNotEmpty() -> { query="";closeInput() }
            sheet.currentValue==SheetValue.Expanded -> { closeInput();scope.launch { sheet.partialExpand() } }
            else -> onBack()
        }
    }
    LaunchedEffect(focusRequest) {
        if(focusRequest>0) {
            // AndroidView receives the new campus/filter before this frame's camera target.
            withFrameNanos { }
            catalogue.firstOrNull { it.id==selectedId }?.let { amap?.focus(it) }
        }
    }
    LaunchedEffect(selectedId,categoryName,campusId,query,catalogue) {
        val index=results.indexOfFirst { it.id==selectedId }
        if(index>=0) listState.animateScrollToItem(index) else listState.scrollToItem(0)
        val catIndex=categories.indexOf(category)
        if(catIndex>=0) categoryState.animateScrollToItem(catIndex)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(if(dark) Color(0xFF192923) else Color(0xFFE7EEE8))
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val heightPx=with(density) { maxHeight.toPx() }
        val sheetHeight=(maxHeight-112.dp).coerceAtLeast(200.dp)
        val peek= minOf(282.dp,(maxHeight*.48f).coerceAtLeast(200.dp))
        val sheetTop by remember(scaffold,heightPx) { derivedStateOf {
            runCatching { sheet.requireOffset() }.getOrDefault(heightPx-with(density) { peek.toPx() })
        } }
        BottomSheetScaffold(scaffoldState=scaffold, modifier=Modifier.fillMaxSize(),
            sheetPeekHeight=peek, sheetShape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp),
            sheetContainerColor=card, sheetContentColor=foreground,
            sheetShadowElevation=12.dp, containerColor=Color.Transparent,
            sheetDragHandle={
                Box(Modifier.fillMaxWidth().height(28.dp).testTag("campus-sheet-handle")
                    .semantics { contentDescription="展开或收起地点面板"; role=Role.Button }
                    .clickable {
                        closeInput();scope.launch {
                            if(sheet.currentValue==SheetValue.Expanded) sheet.partialExpand() else sheet.expand()
                        }
                    },contentAlignment=Alignment.Center) {
                    Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(secondary.copy(alpha=.25f)))
                }
            }, sheetContent={
                Column(Modifier.fillMaxWidth().height(sheetHeight).testTag("campus-place-panel")) {
                    OutlinedTextField(value=query,onValueChange={ query=it },singleLine=true,
                        modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp).heightIn(min=52.dp)
                            .testTag("campus-search").onFocusChanged { if(it.isFocused) scope.launch { sheet.expand() } },
                        placeholder={ Text("搜索地点、楼号、简称",fontSize=14.sp,color=secondary) },
                        leadingIcon={ Icon(Icons.Outlined.Search,null,tint=secondary,modifier=Modifier.size(21.dp)) },
                        trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={ query="" },modifier=Modifier.testTag("campus-search-clear")) {
                            Icon(Icons.Outlined.Close,"清空搜索",tint=secondary,modifier=Modifier.size(19.dp))
                        } },shape=RoundedCornerShape(16.dp),
                        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=CampusGreen,
                            unfocusedBorderColor=Color.Transparent,focusedContainerColor=CampusGreen.copy(alpha=.06f),
                            unfocusedContainerColor=foreground.copy(alpha=.045f),focusedTextColor=foreground,unfocusedTextColor=foreground,cursorColor=CampusGreen),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                        keyboardActions=KeyboardActions(onSearch={ closeInput() }))
                    LazyRow(state=categoryState,modifier=Modifier.fillMaxWidth().padding(top=12.dp).selectableGroup(),
                        contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(categories,key={ it.name }) { item ->
                            val active=!searching && item==category
                            Box(Modifier.heightIn(min=40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if(active) CampusGreen else foreground.copy(alpha=.045f))
                                .testTag("campus-category-${item.name}")
                                .selectable(active,role=Role.Tab,onClick={
                                    closeInput();query="";categoryName=item.name
                                    if(catalogue.firstOrNull { it.id==selectedId }?.let { item!=PlaceCategory.ALL && it.category!=item }==true) selectedId=null
                                    scope.launch { listState.scrollToItem(0) }
                                }).padding(horizontal=14.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                                Text(item.title,fontSize=13.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,
                                    color=if(active) Color.White else secondary)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(searching) "全部校区的搜索结果" else if(category==PlaceCategory.ALL) "探索${campus.title}" else category.title,
                            fontSize=12.sp,color=secondary,modifier=Modifier.weight(1f))
                        Text("${results.size} 个地点",fontSize=12.sp,color=secondary,modifier=Modifier.testTag("campus-result-count"))
                    }
                    if(results.isEmpty()) {
                        Column(Modifier.fillMaxWidth().weight(1f).padding(top=32.dp).testTag("campus-search-empty"),horizontalAlignment=Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.SearchOff,null,tint=secondary.copy(alpha=.55f),modifier=Modifier.size(38.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(if(waitingForPlaces) "正在加载高德校园地点…" else if(loadError!=null) "地点加载失败" else "没有找到相关地点",fontWeight=FontWeight.Medium,color=foreground)
                            Text(if(waitingForPlaces) "首次加载需要一点时间" else if(loadError!=null) loadError else "试试楼号、简称，或“食堂”",fontSize=13.sp,color=secondary,modifier=Modifier.padding(top=6.dp))
                            if(!waitingForPlaces) TextButton(onClick={ if(loadError!=null) reload++ else query="" }) { Text(if(loadError!=null) "重试" else "清空搜索",color=CampusGreen) }
                        }
                    } else {
                        LazyColumn(state=listState,modifier=Modifier.fillMaxWidth().weight(1f).testTag("campus-place-list"),
                            contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            items(results,key={it.id}) { place ->
                                PlaceCard(place,place.id==selectedId,foreground,secondary, onClick={ selectPlace(place) })
                            }
                            if(searching && (loading || geoErrors.isNotEmpty())) item {
                                TextButton(onClick={ if(!loading) reload++ }) {
                                    Text(if(loading) "正在补充其他校区地点…" else "部分校区加载失败，点击重试",fontSize=12.sp,color=CampusGreen)
                                }
                            }
                            item { Text("名称与位置来自高德地图",fontSize=11.sp,color=secondary,
                                modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) }
                        }
                    }
                }
            }) { _ ->
            Box(Modifier.fillMaxSize()) {
                AndroidView(modifier=Modifier.fillMaxSize().testTag("campus-amap-scene"),factory={ viewContext ->
                    CampusAmapView(viewContext,owner,geoCamera).apply {
                        onPlaceClick={ latestSelect(it) }
                        onCameraChanged={ geoCamera=it }
                        amap=this
                    }
                },update={ view ->
                    view.show(campus,geoData[campus],catalogue.filter { it.campus==campus },selectedId,results.map { it.id }.toSet(),dark,
                        with(density) { 130.dp.toPx() },sheetTop)
                },onRelease={ it.dispose();amap=null })
                Box(Modifier.fillMaxWidth().height(145.dp).background(Brush.verticalGradient(
                    listOf(card.copy(alpha=.97f),card.copy(alpha=.88f),Color.Transparent))))
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(top=12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=CircleShape,color=card.copy(alpha=.95f),shadowElevation=2.dp) { AppBackButton(onBack) }
                        Column(Modifier.weight(1f).padding(start=12.dp)) {
                            Text("校园地图",fontSize=22.sp,fontWeight=FontWeight.Bold,color=foreground)
                            Text("SICAU  /  探索你的校园",fontSize=10.sp,letterSpacing=1.sp,color=secondary,modifier=Modifier.padding(top=2.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(shape=RoundedCornerShape(16.dp),color=card.copy(alpha=.96f),shadowElevation=2.dp) {
                        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                            CampusMap.entries.forEach { item ->
                                val active=item==campus
                                Box(Modifier.weight(1f).heightIn(min=42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if(active) CampusGreen else Color.Transparent)
                                    .testTag("campus-tab-${item.id}").selectable(active,role=Role.Tab,onClick={switchCampus(item)}),contentAlignment=Alignment.Center) {
                                    Text(item.title,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=if(active) Color.White else secondary)
                                }
                            }
                        }
                    }
                }
                // Controls stay in the clear area above the sheet; also available to accessibility users.
                val availableControls=with(density) { sheetTop.toDp() } > 380.dp
                if(availableControls) {
                    Column(Modifier.align(Alignment.TopEnd).padding(end=16.dp,top=146.dp).width(46.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        MapControl(Icons.Outlined.CenterFocusStrong,"查看全校",card,foreground) { amap?.resetCamera() }
                        MapControl(Icons.Outlined.Layers,"切换俯视与立体",card,foreground) { amap?.togglePitch() }
                        MapControl(Icons.Outlined.RotateRight,"旋转地图",card,foreground) { amap?.rotateBy(30f) }
                        Surface(shape=RoundedCornerShape(15.dp),color=card.copy(alpha=.96f),shadowElevation=2.dp) {
                            Column {
                                IconButton(onClick={amap?.zoomBy(1.35f)},modifier=Modifier.size(46.dp)) {
                                    Icon(Icons.Outlined.Add,"放大地图",tint=foreground)
                                }
                                HorizontalDivider(Modifier.padding(horizontal=12.dp),color=foreground.copy(alpha=.08f))
                                IconButton(onClick={amap?.zoomBy(1/1.35f)},modifier=Modifier.size(46.dp)) {
                                    Icon(Icons.Outlined.Remove,"缩小地图",tint=foreground)
                                }
                            }
                        }
                    }
                    Surface(Modifier.align(Alignment.TopStart).padding(start=16.dp,end=78.dp,top=148.dp)
                        .clickable(enabled=loadError!=null) { reload++ }.testTag("campus-map-status"),
                        shape=RoundedCornerShape(8.dp),color=card.copy(alpha=.82f)) {
                        Text(mapStatus,fontSize=10.sp,color=secondary,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
                    }
                    Box(Modifier.offset(y=with(density) { sheetTop.toDp() }-34.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        Text("双指缩放 / 旋转 · 双指上下滑动倾斜",fontSize=10.sp,color=secondary,
                            modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(card.copy(alpha=.76f)).padding(horizontal=10.dp,vertical=5.dp))
                    }
                }
            }
        }
    }

}

@Composable
private fun PlaceCard(place: CampusPlace, selected: Boolean, foreground: Color, secondary: Color, onClick: () -> Unit) {
    val tint=Color(place.category.color)
    Surface(modifier=Modifier.fillMaxWidth().testTag("campus-place-${place.id}")
        .semantics { this.selected=selected }.clip(RoundedCornerShape(16.dp)).clickable(onClick=onClick),
        shape=RoundedCornerShape(16.dp),color=if(selected) CampusGreen.copy(alpha=.08f) else foreground.copy(alpha=.035f),
        border=if(selected) BorderStroke(1.3.dp,CampusGreen) else BorderStroke(1.dp,Color.Transparent)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha=.17f)),contentAlignment=Alignment.Center) {
                Icon(categoryIcon(place.category),null,tint=tint,modifier=Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text(place.name,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=foreground,maxLines=3,overflow=TextOverflow.Ellipsis)
                Text("${place.campus.title} · ${place.category.title}",fontSize=11.sp,color=secondary,modifier=Modifier.padding(top=4.dp))
            }
            Icon(if(selected) Icons.Outlined.CheckCircle else Icons.Outlined.NearMe,null,
                tint=if(selected) CampusGreen else secondary.copy(alpha=.5f),modifier=Modifier.size(19.dp))
        }
    }
}
private fun categoryIcon(category: PlaceCategory): ImageVector = when(category) {
    PlaceCategory.TEACHING -> Icons.Outlined.AccountBalance
    PlaceCategory.DORM -> Icons.Outlined.Apartment
    PlaceCategory.DINING -> Icons.Outlined.Restaurant
    PlaceCategory.LIBRARY -> Icons.Outlined.LocalLibrary
    PlaceCategory.SPORTS -> Icons.Outlined.SportsBasketball
    PlaceCategory.GATE -> Icons.Outlined.Login
    else -> Icons.Outlined.Place
}
@Composable
private fun MapControl(icon: ImageVector,label: String,card: Color,foreground: Color,onClick: () -> Unit) {
    Surface(shape=RoundedCornerShape(15.dp),color=card.copy(alpha=.96f),shadowElevation=2.dp) {
        IconButton(onClick=onClick,modifier=Modifier.size(46.dp)) { Icon(icon,label,tint=foreground,modifier=Modifier.size(22.dp)) }
    }
}
