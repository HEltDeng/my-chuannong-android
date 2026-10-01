package com.mychuannong.app.freshNews.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.freshNews.data.local.mmkv.model.FreshNewsItem

@Composable
fun NewsItem(
    item: FreshNewsItem,
    onImageClick: (List<String>, Int) -> Unit,
    onUserClick: (Int) -> Unit,
    onMenuClick: (FreshNewsItem) -> Unit,
    onLikeClick: (Int) -> Unit,
    onCommentClick: (FreshNewsItem) -> Unit,
    onShareClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AppTheme.colors.bgCardColor)
            .clickable { }
            .padding(16.dp)
    ) {
        // Header: Avatar + Name + Time/Location + More
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.authorAvatar,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onUserClick(item.userId) },
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_default_avatar),
                error = painterResource(id = R.drawable.ic_default_avatar)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.authorName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.createTime,
                        fontSize = 12.sp,
                        color = AppTheme.colors.greyTextColor
                    )

                    if (item.location.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_one_point),
                            contentDescription = null,
                            modifier = Modifier.size(4.dp),
                            tint = AppTheme.colors.greyTextColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.location,
                            fontSize = 12.sp,
                            color = AppTheme.colors.greyTextColor
                        )
                    }
                }
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_more),
                contentDescription = "More",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onMenuClick(item) },
                tint = AppTheme.colors.iconSecondaryColor
            )
        }

        // Title
        if (item.title.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.primaryTextColor,
                lineHeight = 24.sp
            )
        }

        // Content
        if (item.content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.content,
                fontSize = 15.sp,
                color = AppTheme.colors.primaryTextColor,
                lineHeight = 22.sp
            )
        }

        if (item.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.tags.filter { it.isNotBlank() }.take(3).forEach { tag ->
                    Text(
                        text = "#${tag.trim().removePrefix("#")}",
                        color = AppTheme.colors.commonColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.bgButtonLowlightColor)
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Images Grid
        if (item.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            NewsImageGrid(
                images = item.images,
                onImageClick = { index -> onImageClick(item.images, index) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interaction Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Like
            InteractionButton(
                icon = if (item.isLiked) R.drawable.ic_news_liked else R.drawable.ic_like,
                count = item.liked,
                tint = if (item.isLiked) Color.Red else AppTheme.colors.iconSecondaryColor,
                onClick = { onLikeClick(item.freshNewsId) }
            )

            // Comment
            InteractionButton(
                icon = R.drawable.ic_comment,
                count = item.comments,
                tint = AppTheme.colors.iconSecondaryColor,
                onClick = { onCommentClick(item) }
            )

            // Collect/Share
            InteractionButton(
                icon = if (item.isFavorited) R.drawable.ic_collect else R.drawable.ic_un_collect,
                count = item.favoritesCount,
                tint = if (item.isFavorited) Color(0xFFFFD000) else AppTheme.colors.iconSecondaryColor,
                onClick = { onShareClick(item.freshNewsId) }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
fun NewsImageGrid(
    images: List<String>,
    onImageClick: (Int) -> Unit
) {
    val imageCount = images.size
    val rows = (imageCount + 2) / 3 // ceil(count / 3)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (j in 0 until 3) {
                    val index = i * 3 + j
                    if (index < imageCount) {
                        AsyncImage(
                            model = images[index],
                            contentDescription = "Image $index",
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(index) },
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_image_placeholder),
                            error = painterResource(id = R.drawable.ic_image_placeholder)
                        )
                    } else {
                        // Empty spacer to transform weight correctly if less than 3 items in a row
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionButton(
    icon: Int,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                fontSize = 14.sp,
                color = AppTheme.colors.primaryTextColor
            )
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = " ", // Placeholder to keep height or just 0
                fontSize = 14.sp,
                color = AppTheme.colors.primaryTextColor
            )
        }
    }
}
