package com.mychuannong.app.freshNews.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.feature.lostfound.ui.LostFoundActivity
import com.mychuannong.app.freshNews.data.local.mmkv.CampusCommunityLocalStore
import com.mychuannong.app.freshNews.data.local.model.LostFoundStatus
import com.mychuannong.app.freshNews.ui.compose.LocalCampusPostDetailScreen

class LocalCampusPostDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val postId = intent.getStringExtra(EXTRA_POST_ID).orEmpty()
        var post by mutableStateOf(postId.takeIf(String::isNotBlank)
            ?.let(CampusCommunityLocalStore::findPost)
        )
        if (post?.lostFound != null) {
            startActivity(LostFoundActivity.detailIntent(this, postId))
            finish()
            return
        }
        post?.linkedActivityId?.let { linkedId ->
            Route.goBuddyActivityDetail(this, linkedId)
            finish()
            return
        }

        setContent {
            AppSkinTheme {
                LocalCampusPostDetailScreen(
                    post = post,
                    onBackClick = ::finish,
                    onFavorite = { post?.let { post = CampusCommunityLocalStore.toggleFavorite(it.id) } },
                    onDelete = { post?.let { CampusCommunityLocalStore.deleteLocalPost(it.id) }; finish() },
                    onReport = { reason -> post?.let { CampusCommunityLocalStore.recordReport(it.id, reason) } },
                    onLostFoundStatus = { status -> post?.let { post = CampusCommunityLocalStore.updateLostFoundStatus(it.id, status) } },
                    onLocalClaim = { post?.let { post = CampusCommunityLocalStore.submitLocalClaim(it.id) } }
                )
            }
        }
    }

    companion object {
        const val EXTRA_POST_ID = "local_campus_post_id"
    }
}
