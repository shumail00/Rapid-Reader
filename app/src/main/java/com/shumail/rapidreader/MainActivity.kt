package com.shumail.rapidreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.shumail.rapidreader.ui.screens.HomeScreen
import com.shumail.rapidreader.ui.screens.RsvpReadingScreen
import com.shumail.rapidreader.ui.theme.RsvpAppTheme
import com.shumail.rapidreader.viewmodel.RsvpViewModel
import com.shumail.rapidreader.viewmodel.ScreenDestination

class MainActivity : ComponentActivity() {

    private val viewModel: RsvpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RsvpAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RsvpMainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RsvpMainApp(viewModel: RsvpViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val savedDocs by viewModel.savedDocuments.collectAsState()

    // Handle system back navigation when in reading screen
    BackHandler(enabled = uiState.currentScreen != ScreenDestination.HOME) {
        viewModel.backToHome()
    }

    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = {
            if (targetState == ScreenDestination.READER || targetState == ScreenDestination.COMPLETION_SUMMARY) {
                (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300)))
            } else {
                (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300)))
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            ScreenDestination.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    savedDocuments = savedDocs
                )
            }
            ScreenDestination.READER, ScreenDestination.COMPLETION_SUMMARY -> {
                RsvpReadingScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}
