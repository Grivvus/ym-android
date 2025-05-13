package sstu.grivvus.yamusic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.sharp.AddToHomeScreen
import androidx.compose.material.icons.automirrored.sharp.InsertDriveFile
import androidx.compose.material.icons.automirrored.sharp.Note
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import sstu.grivvus.yamusic.ui.theme.appIcons
import sstu.grivvus.yamusic.ui.theme.Purple80


@Preview(showBackground = true)
@Composable
fun BottomBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(45.dp).background(Purple80),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier
                .clickable(
                    enabled = true, onClick = {
                        TODO("Go to music page")
                    }
                )
        ) { appIcons.Note }
        Column(
            modifier.clickable(enabled = true, onClick = {
                TODO("Go to upload page")
            })
        ) { appIcons.InsertDriveFile }
        Column(
            modifier.clickable(enabled = true, onClick = {
                TODO("Go to profile page")
            })
        ) {
            appIcons.AddToHomeScreen
        }
    }
}