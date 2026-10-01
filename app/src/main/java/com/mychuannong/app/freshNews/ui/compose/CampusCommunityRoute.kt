package com.mychuannong.app.freshNews.ui.compose

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mychuannong.app.core.Route
import com.mychuannong.app.freshNews.viewModel.LocalCampusViewModel

/** Device-only campus channel entry. No legacy account or remote repository is used here. */
@Composable
fun CampusCommunityRoute(
    viewModel: LocalCampusViewModel = viewModel(),
    initialCategory: String? = null,
) {
    val context = LocalContext.current
    val publishLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.showLatestPostsAfterPublish()
        }
    }
    LaunchedEffect(initialCategory) {
        initialCategory?.let(viewModel::selectCategory)
    }
    LocalCampusChannelScreen(
        viewModel = viewModel,
        onPublishClick = {
            publishLauncher.launch(Route.localCampusPublishIntent(context))
        },
        onPostClick = { postId -> Route.goLocalCampusPostDetail(context, postId) }
    )
}
