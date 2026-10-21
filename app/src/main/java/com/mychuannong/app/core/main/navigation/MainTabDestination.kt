package com.mychuannong.app.core.main.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.mychuannong.app.R

sealed class MainTabDestination(
    val index: Int,
    @param:DrawableRes @get:DrawableRes val iconResId: Int,
    @param:StringRes @get:StringRes val labelResId: Int
) : NavKey {

    data object Overview : MainTabDestination(
        index = 0,
        iconResId = R.drawable.ic_overview,
        labelResId = R.string.overview
    )

    data object Feature : MainTabDestination(
        index = 1,
        iconResId = R.drawable.nfeature,
        labelResId = R.string.function
    )

    data object Profile : MainTabDestination(
        index = 2,
        iconResId = R.drawable.nprofile,
        labelResId = R.string.profile_home
    )

    companion object {
        /**
         * The single source of truth for the main tab order. Pager pages, bottom bar items and
         * externally requested tab indexes must all resolve through this list.
         */
        // Build the list on access instead of during superclass initialization. Initializing one
        // of the nested data objects first makes ART initialize MainTabDestination first; a static
        // list here could then observe that data object's INSTANCE before it has been assigned.
        val ordered: List<MainTabDestination>
            get() = listOf(
                Overview,
                Feature,
                Profile
            )

        fun fromIndex(index: Int): MainTabDestination? = ordered.getOrNull(index)

        fun indexOf(destination: MainTabDestination): Int? =
            ordered.indexOf(destination).takeIf { it >= 0 }
    }
}
