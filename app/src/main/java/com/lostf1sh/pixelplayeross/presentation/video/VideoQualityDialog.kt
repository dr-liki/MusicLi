package com.lostf1sh.pixelplayeross.presentation.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.data.video.SyncedVideoStreamBundle

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Quality") },
        text = {
            LazyColumn {
                items(qualities) { (height, label) ->
                    val isSelected = if (height == null) selectedHeight == null || selectedHeight <= 0 else selectedHeight == height
                    Surface(
                        onClick = {
                            onSelectQuality(height)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
