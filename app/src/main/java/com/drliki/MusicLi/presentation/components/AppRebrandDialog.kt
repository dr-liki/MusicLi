package com.drliki.MusicLi.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drliki.MusicLi.R

@Composable
fun AppRebrandDialog(
    onDismiss: () -> Unit,
    onDoNotShowAgain: (Boolean) -> Unit
) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Info,
        title = stringResource(id = R.string.app_name_change_title),
        confirmText = stringResource(id = R.string.dismiss),
        onConfirm = {
            onDoNotShowAgain(doNotShowAgain)
            onDismiss()
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(id = R.string.app_name_change_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { doNotShowAgain = !doNotShowAgain }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = doNotShowAgain,
                    onCheckedChange = { doNotShowAgain = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.do_not_show_again),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}