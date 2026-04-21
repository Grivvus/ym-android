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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.sharp.AddAPhoto
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomBar
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.data.AppLanguage
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.data.network.model.toDisplayNameRes
import sstu.grivvus.ym.passwordChange.PasswordChangeDialog
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
    var showSettingsDialog by remember { mutableStateOf(false) }
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
                if (showSettingsDialog) {
                    ProfileSettingsDialog(
                        selectedLanguage = uiState.selectedAppLanguage,
                        onLanguageSelected = { language ->
                            showSettingsDialog = false
                            if (language != uiState.selectedAppLanguage) {
                                viewModel.changeAppLanguage(language)
                            }
                        },
                        onDismiss = { showSettingsDialog = false },
                    )
                }
                if (showPasswordDialog) {
                    PasswordChangeDialog(
                        onDismiss = { showPasswordDialog = false }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.profile_cd_open_settings),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(120.dp))
                    } else {
                        AsyncImage(
                            model = uiState.avatarUri,
                            contentDescription = stringResource(R.string.profile_cd_user_avatar),
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
                            contentDescription = stringResource(R.string.profile_cd_upload_avatar)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                UserInfoItem(
                    label = stringResource(R.string.common_label_username),
                    value = uiState.username,
                ) { viewModel.changeUsername(it) }

                Spacer(Modifier.height(16.dp))

                UserInfoItem(
                    label = stringResource(R.string.common_label_email),
                    value = uiState.email ?: "",
                    { viewModel.changeEmail(it) },
                )

                Spacer(Modifier.height(32.dp))

                UserInfoItem(
                    label = stringResource(R.string.common_label_server_host),
                    value = uiState.serverHost,
                ) { viewModel.changeServerHost(it) }

                Spacer(Modifier.height(32.dp))

                UserInfoItem(
                    label = stringResource(R.string.common_label_server_port),
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
                        Text(stringResource(R.string.common_action_change_password))
                    }
                }
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column {
                        Button(onClick = { viewModel.tryToApplyChanges() }) {
                            Text(stringResource(R.string.common_action_save_changes))
                        }
                    }
                    Column {
                        IconButton(onClick = viewModel::logOut) {
                            Icon(
                                appIconsMirrored.Logout,
                                stringResource(R.string.profile_cd_logout_button),
                            )
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

@Composable
private fun ProfileSettingsDialog(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.profile_settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                Text(
                    text = stringResource(R.string.profile_settings_language_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(language) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedLanguage == language,
                            onClick = { onLanguageSelected(language) },
                        )
                        Text(
                            text = stringResource(language.toLabelResId()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_action_dismiss))
            }
        },
    )
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
            text = stringResource(R.string.profile_title_playback_quality),
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
                    text = stringResource(quality.toDisplayNameRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Text(
            text = stringResource(R.string.profile_playback_quality_hint),
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

private fun AppLanguage.toLabelResId(): Int =
    when (this) {
        AppLanguage.SYSTEM_DEFAULT -> R.string.profile_settings_language_system_default
        AppLanguage.ENGLISH -> R.string.profile_settings_language_english
        AppLanguage.RUSSIAN -> R.string.profile_settings_language_russian
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
