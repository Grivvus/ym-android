package sstu.grivvus.ym.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun BottomNavScaffold(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        bottomBar = {
            BottomBar(
                onMusicClick = navigateToMusic,
                onLibraryClick = navigateToLibrary,
                onProfileClick = navigateToProfile,
            )
        },
        content = content,
    )
}

@Composable
fun ScreenStateHost(
    isLoading: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable BoxScope.() -> Unit = {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    },
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (isLoading) {
            loadingContent()
        } else {
            content()
        }

        ErrorSnackbar(
            errorMessage = errorMessage,
            onDismiss = onDismissError,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
