package com.mychuannong.app.feature.common.compose_ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mychuannong.app.ElectronicAppWidget
import com.mychuannong.app.R
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.contract.ElectronicContract
import com.mychuannong.app.feature.common.viewModel.ElectronicViewModel
import com.tencent.mmkv.MMKV

private val ALPHANUMERIC_REGEX = Regex("^[a-zA-Z0-9]*$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectronicScreen(
    viewModel: ElectronicViewModel,
    onBack: () -> Unit
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val mmkv = remember { MMKV.defaultMMKV() }

    var schoolText by remember { mutableStateOf("选择校区") }
    var dormText by remember { mutableStateOf("选择宿舍楼") }
    var roomText by remember { mutableStateOf("") }
    var showSchoolSheet by remember { mutableStateOf(false) }
    var showDormSheet by remember { mutableStateOf(false) }

    val schoolList = remember {
        com.mychuannong.app.school.SchoolServiceCatalog.campuses
    }
    val dormList = remember {
        emptyList<String>() // No verified school building catalog yet.
    }

    // Restore state from MMKV on first composition
    LaunchedEffect(Unit) {
        val savedSchool = mmkv.decodeString("school", "选择校区") ?: "选择校区"
        val savedDor = mmkv.decodeString("dor", "选择宿舍楼") ?: "选择宿舍楼"
        val savedDoor = mmkv.decodeString("door_number", "") ?: ""

        schoolText = savedSchool
        dormText = savedDor
        roomText = savedDoor

        viewModel.processIntent(
            ElectronicContract.Intent.Init(savedSchool, savedDor, savedDoor)
        )
    }

    // Save state to MMKV when leaving
    DisposableEffect(Unit) {
        onDispose {
            mmkv.encode("school", schoolText)
            mmkv.encode("dor", dormText)
            mmkv.encode("door_number", roomText)
        }
    }

    // Bottom sheets
    if (showSchoolSheet) {
        SelectionBottomSheet(
            items = schoolList,
            onDismiss = { showSchoolSheet = false },
            onSelect = { selected ->
                schoolText = selected
                dormText = "选择宿舍楼"
                viewModel.processIntent(ElectronicContract.Intent.SelectSchool(selected))
            }
        )
    }

    if (showDormSheet) {
        val filteredDorms = dormList
        SelectionBottomSheet(
            items = filteredDorms,
            onDismiss = { showDormSheet = false },
            onSelect = { selected ->
                dormText = selected
                viewModel.processIntent(ElectronicContract.Intent.SelectDorm(selected))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.campusSnowColor)
    ) {
        com.mychuannong.app.core.theme.SecondaryScaffoldHeader("电费查询", onBack)

        // ── Scrollable Content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Section label
            Text(
                text = "寝室绑定",
                fontSize = 13.sp,
                fontWeight = FontWeight.W500,
                color = colors.campusSlateColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // ── Binding Card ──
            CampusCard {
                SelectorRow(
                    label = "校区",
                    value = schoolText,
                    onClick = {
                        focusManager.clearFocus()
                        showSchoolSheet = true
                    }
                )

                CampusDivider()

                SelectorRow(
                    label = "宿舍楼",
                    value = dormText,
                    onClick = {
                        focusManager.clearFocus()
                        android.widget.Toast.makeText(context,"暂时无法获取宿舍信息，请稍后重试",android.widget.Toast.LENGTH_SHORT).show()
                    }
                )

                CampusDivider()

                // Room input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "房间号",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W500,
                        color = colors.campusInkColor
                    )
                    ImeCompatibleTextField(
                        value = roomText,
                        onValueChange = { newText: String ->
                            if (ALPHANUMERIC_REGEX.matches(newText)) {
                                roomText = newText
                            }
                        },
                        modifier = Modifier.width(160.dp),
                        placeholder = "填写房间号"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            CampusCard {
                ElectricityResult(state = state)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("宿舍信息暂不可用", color = colors.campusSlateColor, modifier = Modifier.padding(bottom = 12.dp))
            Button(
                enabled = dormList.isNotEmpty() && dormText != "选择宿舍楼" && roomText.isNotBlank() && !state.isLoading,
                onClick = {
                    focusManager.clearFocus()
                    val processedDoorNumber = roomText
                    viewModel.processIntent(
                        ElectronicContract.Intent.QueryElectricity(
                            schoolText,
                            dormText,
                            processedDoorNumber
                        )
                    )
                    mmkv.encode("school", schoolText)
                    mmkv.encode("dor", dormText)
                    mmkv.encode("door_number", roomText)
                    refreshWidget()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.campusSkyBlueColor,
                    contentColor = Color.White
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "查询电量",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500
                    )
                }
            }
        }
    }
}

@Composable
private fun CampusCard(content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.bgCardColor),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
    ) {
        content()
    }
}

@Composable
private fun CampusDivider() {
    HorizontalDivider(
        color = AppTheme.colors.campusDividerColor,
        modifier = Modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun SelectorRow(label: String, value: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.W500,
            color = colors.campusInkColor
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.campusSkyBlueLightColor)
                .border(1.dp, colors.campusSkyBlueColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = colors.campusSkyBlueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.W500
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = colors.campusSkyBlueColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private enum class EleState { LOW, NORMAL, HIGH, UNKNOWN, ERROR }

@Composable
private fun ElectricityResult(state: ElectronicContract.State) {
    val colors = AppTheme.colors

    val eleState = remember(state.elec, state.isElec) {
        if (!state.isElec) EleState.UNKNOWN
        else {
            val regex = Regex("(\\d*\\.?\\d+)")
            val match = regex.find(state.elec)
            val value = match?.value?.toFloatOrNull()
            when {
                value == null -> if (state.elec == "无数据" || state.elec == "查询失败") EleState.ERROR else EleState.UNKNOWN
                value in 0.0f..20f -> EleState.LOW
                value in 20.1f..100f -> EleState.NORMAL
                value > 100f -> EleState.HIGH
                else -> EleState.UNKNOWN
            }
        }
    }

    val displayValue = remember(state.elec, state.isElec) {
        if (!state.isElec) "--"
        else {
            val regex = Regex("(\\d*\\.?\\d+)")
            val match = regex.find(state.elec)
            match?.value ?: when (state.elec) {
                "无数据" -> "无数据"
                "查询失败" -> "查询失败"
                else -> "--"
            }
        }
    }

    val stateText = when (eleState) {
        EleState.LOW -> "电量过低"
        EleState.NORMAL -> "电量正常"
        EleState.HIGH -> "电量充足"
        EleState.UNKNOWN -> "状态未知"
        EleState.ERROR -> state.elec
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "当前电量",
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            color = colors.campusSlateColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(
                    when (eleState) {
                        EleState.LOW -> R.drawable.ic_electricity_none
                        EleState.NORMAL -> R.drawable.ic_electricity_low
                        EleState.HIGH -> R.drawable.ic_electricity_high
                        EleState.UNKNOWN -> R.drawable.ic_electricity_default
                        EleState.ERROR -> R.drawable.ic_electricity_default
                    }
                ),
                contentDescription = stateText,
                modifier = Modifier.size(50.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(40.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displayValue,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.W600,
                    color = colors.campusInkColor,
                    letterSpacing = (-0.5).sp,
                    style = TextStyle(fontFeatureSettings = "\"tnum\"")
                )
                if (eleState != EleState.ERROR && eleState != EleState.UNKNOWN) {
                    Text(
                        text = " kWh",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500,
                        color = colors.campusSlateColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = when (eleState) {
                    EleState.LOW -> colors.campusCoralColor.copy(alpha = 0.12f)
                    EleState.NORMAL -> colors.campusAmberColor.copy(alpha = 0.12f)
                    EleState.HIGH -> colors.campusMintColor.copy(alpha = 0.12f)
                    EleState.UNKNOWN -> colors.campusCloudColor
                    EleState.ERROR -> colors.campusCloudColor
                }
            ) {
                Text(
                    text = stateText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = when (eleState) {
                        EleState.LOW -> colors.campusCoralColor
                        EleState.NORMAL -> colors.campusAmberColor
                        EleState.HIGH -> colors.campusMintColor
                        EleState.UNKNOWN -> colors.campusSlateColor
                        EleState.ERROR -> colors.campusSlateColor
                    }
                )
            }
        }
    }
}

private fun processDormAndRoom(dor: String, doorNumber: String): String {
    val containsA = dor.contains('A')
    val containsB = dor.contains('B')
    val doorContainsLetter = doorNumber.any { it.isLetter() }
    return when {
        containsA && !doorContainsLetter -> "A$doorNumber"
        containsB && !doorContainsLetter -> "B$doorNumber"
        else -> doorNumber
    }
}

private fun refreshWidget() {
    val appWidgetManager = AppWidgetManager.getInstance(PlanetApplication.appContext)
    val componentName = ComponentName(PlanetApplication.appContext, ElectronicAppWidget::class.java)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

    if (appWidgetIds.isNotEmpty()) {
        val intent = Intent(PlanetApplication.appContext, ElectronicAppWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        PlanetApplication.appContext.sendBroadcast(intent)
    }
}
