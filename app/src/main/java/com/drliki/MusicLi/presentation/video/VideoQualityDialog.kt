package com.drliki.MusicLi.presentation.video

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drliki.MusicLi.data.video.SyncedVideoStreamBundle
import com.drliki.MusicLi.presentation.components.MusicLiDialog

@Composable
fun VideoQualityDialog(
    bundle: SyncedVideoStreamBundle,
    selectedHeight: Int?,
    onSelectQuality: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val qualities = buildList {
        add(null to "Auto")
        bundle.availableStreams
            .mapNotNull { it.height }
            .distinct()
            .sortedDescending()
            .forEach { height ->
                val label = bundle.availableStreams.firstOrNull { it.height == height }?.displayQuality ?: "${height}p"
                add(height to label)
            }
    }

    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.HighQuality,
        title = "Video Quality",
        scrollable = false,
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(qualities) { (height, label) ->
                val isSelected = if (height == null) selectedHeight == null || selectedHeight <= 0 else selectedHeight == height
                Surface(
                    onClick = {
                        onSelectQuality(height)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
