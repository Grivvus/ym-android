package sstu.grivvus.yamusic.profile

import android.widget.DatePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import sstu.grivvus.yamusic.AppTopBar
import sstu.grivvus.yamusic.R
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import java.time.Instant
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {

}

@Composable
fun ProfileScreenUI(
    modifier: Modifier = Modifier
) {
    var userLoginState = remember { mutableStateOf("user login 123") }
    var userFirstNameState = remember { mutableStateOf("user first name") }
    var userSecondNameState = remember { mutableStateOf("user second name") }
    var userBirthdayDateState = remember {
        mutableStateOf(Instant.now().toEpochMilli())
    }

    YaMusicTheme {
        Scaffold(
            topBar = {AppTopBar()}
        ) { innerPadding ->
            Column(modifier = modifier.padding(innerPadding)) {
                Spacer(modifier.height(15.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.test_profile_image
                        ),
                        contentDescription = "Profile Image",
                        contentScale = ContentScale.Crop,
                        modifier = modifier.size(100.dp).clip(CircleShape)
                    )
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ){
                    ProfileField(userLoginState, "login")
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ProfileField(userFirstNameState, "first name")
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ProfileField(userSecondNameState, "second name")
                }
                Spacer(modifier.height(15.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DatePickerDocked(userBirthdayDateState)
                }
                Spacer(modifier.height(35.dp))
                Row(
                    modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(
                        onClick = {TODO("Edit profile button implementation")},
                        modifier.width(180.dp)
                    ) {
                        Text("Save")
                    }
                    Button(
                        onClick = {
                            TODO("Change password button implementation")
                        },
                        modifier.width(180.dp)
                    ) {
                        Text("Change password")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileField(
    textState: MutableState<String>,
    placeholderText: String = "",
) {
    TextField(
        textState.value, {textState.value = it},
        placeholder = {
            Text(placeholderText)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDocked(dateState: MutableState<Long>) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateState.value)
    val selectedDate = datePickerState.selectedDateMillis?.let {
        convertMillisToDate(it)
    } ?: ""

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = "Birth date is $selectedDate",
            onValueChange = { },
            label = { Text("") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = !showDatePicker }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Выберите дату"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        )

        if (showDatePicker) {
            Popup(
                onDismissRequest = {
                    dateState.value = datePickerState.selectedDateMillis ?: 0
                    showDatePicker = false
                },
                alignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 64.dp)
                        .shadow(elevation = 4.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewProfileScreen(){
    ProfileScreenUI()
}