package com.saggitt.omega.compose.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavGraphBuilder
import com.google.accompanist.navigation.animation.composable

@OptIn(ExperimentalAnimationApi::class)
inline fun NavGraphBuilder.preferenceGraph(
    route: String,
    crossinline root: @Composable () -> Unit,
    crossinline block: NavGraphBuilder.(subRoute: (String) -> String) -> Unit = { }
) {
    val subRoute: (String) -> String = { name -> "$route$name/" }
    composable(route = route) {
        CompositionLocalProvider(LocalRoute provides route) {
            root()
        }
    }
    block(subRoute)
}

val LocalRoute = compositionLocalOf { "" }

@Composable
fun subRoute(name: String) = "${LocalRoute.current}$name/"