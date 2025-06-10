package sstu.grivvus.yamusic.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.sharp.Logout
import androidx.compose.material.icons.sharp.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import sstu.grivvus.yamusic.R
import sstu.grivvus.yamusic.components.BottomBar
import sstu.grivvus.yamusic.components.ErrorTooltip
import sstu.grivvus.yamusic.getAvatarUrl
import sstu.grivvus.yamusic.passwordChange.PasswordChangeDialog
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.ui.theme.appIconsMirrored

@Composable
fun ProfileScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    onLogOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.uploadAvatar(context, it) }
        }
    )

    YaMusicTheme {
        Scaffold(
            bottomBar = {BottomBar(navigateToMusic, navigateToLibrary, navigateToProfile)}
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                    { viewModel.changeUsername(it) },
                )

                Spacer(Modifier.height(16.dp))

                UserInfoItem(
                    label = "Email",
                    value = uiState.email ?: "",
                    { viewModel.changeEmail(it) },
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround){
                    Column {
                        Button(onClick = { viewModel.tryToApplyChanges() }) {
                            Text("Save changes")
                        }
                    }
                    Column {
                        IconButton({ viewModel.logOut(); onLogOut() }) {
                            Icon(appIconsMirrored.Logout, "Logout button")
                        }
                    }
                }
            }
        }
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

//@Preview(showBackground = true)
//@Composable
//fun PreviewProfileScreen(){
//    ProfileScreenUI()
//}