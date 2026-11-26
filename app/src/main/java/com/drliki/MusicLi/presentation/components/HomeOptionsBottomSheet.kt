package com.drliki.MusicLi.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drliki.MusicLi.R

@Composable
fun HomeOptionsBottomSheet(
    onNavigateToMashup: () -> Unit,
    showHomePlayQueue: Boolean = true,
    onToggleShowHomePlayQueue: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(bottom = 32.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.setcat_home_play_queue_title)) },
            supportingContent = { Text(stringResource(R.string.setcat_home_play_queue_subtitle)) },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.setcat_home_play_queue_title)
                )
            },
            trailingContent = {
                Switch(
                    checked = showHomePlayQueue,
                    onCheckedChange = onToggleShowHomePlayQueue
                )
            },
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onToggleShowHomePlayQueue(!showHomePlayQueue) }
        )
        ListItem(
            content = { Text(stringResource(R.string.home_option_dj_mashup)) },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                    contentDescription = stringResource(R.string.home_option_dj_mashup)
                )
            },
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onNavigateToMashup)
        )
    }
}