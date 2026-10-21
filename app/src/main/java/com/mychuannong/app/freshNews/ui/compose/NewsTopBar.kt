package com.mychuannong.app.freshNews.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.UserInfoManager
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField

@Composable
fun NewsTopBar(
    currentTab: Int,
    searchQuery: String,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.overviewPageBackgroundColor)
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园频道",
                    color = colors.primaryTextColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "川农同学的校园广场",
                    color = colors.secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = UserInfoManager.userAvatar,
                contentDescription = "个人主页",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_default_avatar),
                error = painterResource(id = R.drawable.ic_default_avatar)
            )
        }

        ImeCompatibleTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            placeholder = "搜索帖子、话题或同学",
            showSearchIcon = true
        )

        val tabs = listOf("最新", "热门")
        PrimaryTabRow(
            modifier = Modifier.fillMaxWidth(),
            selectedTabIndex = currentTab.coerceIn(tabs.indices),
            containerColor = colors.overviewPageBackgroundColor,
            contentColor = colors.primaryTextColor,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(currentTab.coerceIn(tabs.indices)),
                    color = colors.commonColor
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == index) colors.commonColor else colors.secondaryTextColor
                        )
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewsTopBarPreview() {
    AppSkinTheme {
        NewsTopBar(
            currentTab = 0,
            searchQuery = "",
            onTabSelected = {},
            onSearchQueryChange = {}
        )
    }
}
