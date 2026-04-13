package sstu.grivvus.ym.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.automirrored.sharp.Logout
import androidx.compose.material.icons.sharp.AddAPhoto
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomBar
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.data.network.model.toDisplayName
import sstu.grivvus.ym.passwordChange.PasswordChangeDialog
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIcons
import sstu.grivvus.ym.ui.theme.appIconsMirrored

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProfileScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refreshUser,
    )

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.uploadAvatar(context, it) }
        }
    )

    YMTheme {
        Scaffold(
            bottomBar = { BottomBar(navigateToMusic, navigateToLibrary, navigateToProfile) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pullRefresh(refreshState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ErrorTooltip(
                        uiState.errorMsg ?: "",
                        uiState.errorMsg != null,
                        onTimeout = { viewModel.dismissErrorMessage() },
                    )
                    if (showPasswordDialog) {
                        PasswordChangeDialog(
                            onDismiss = { showPasswordDialog = false }
                        )
                    }
                    Spacer(Modifier.height(50.dp))
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(120.dp))
                        } else {
                            AsyncImage(
                                model = uiState.avatarUri,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.ic_placeholder_avatar)
                            )
                        }

                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = appIcons.AddAPhoto,
                                contentDescription = "Upload avatar"
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    UserInfoItem(
                        label = "Username",
                        value = uiState.username,
                    ) { viewModel.changeUsername(it) }

                    Spacer(Modifier.height(16.dp))

                    UserInfoItem(
                        label = "Email",
                        value = uiState.email ?: "",
                        { viewModel.changeEmail(it) },
                    )

                    Spacer(Modifier.height(32.dp))

                    UserInfoItem(
                        label = "Server host",
                        value = uiState.serverHost,
                    ) { viewModel.changeServerHost(it) }

                    Spacer(Modifier.height(32.dp))

                    UserInfoItem(
                        label = "Server port",
                        value = uiState.serverPort,
                        { viewModel.changeServerPort(it) }
                    )

                    Spacer(Modifier.height(32.dp))

                    TrackQualitySettings(
                        selectedQuality = uiState.preferredTrackQuality,
                        onQualitySelected = viewModel::changePreferredTrackQuality,
                    )

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = { showPasswordDialog = true },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(48.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Change Password")
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column {
                            Button(onClick = { viewModel.tryToApplyChanges() }) {
                                Text("Save changes")
                            }
                        }
                        Column {
                            IconButton(onClick = viewModel::logOut) {
                                Icon(appIconsMirrored.Logout, "Logout button")
                            }
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = uiState.isRefreshing,
                    state = refreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun TrackQualitySettings(
    selectedQuality: TrackQuality,
    onQualitySelected: (TrackQuality) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        Text(
            text = "Playback quality",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TrackQuality.entries.forEach { quality ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQualitySelected(quality) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedQuality == quality,
                    onClick = { onQualitySelected(quality) },
                )
                Text(
                    text = quality.toDisplayName(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Text(
            text = "The new quality will be used from the next track start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(
            Modifier.padding(top = 8.dp), 1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun UserInfoItem(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
        Modifier.padding(top = 8.dp)
        HorizontalDivider(
            Modifier.padding(top = 8.dp), 1.dp,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
