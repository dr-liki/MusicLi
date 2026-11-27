package com.drliki.MusicLi.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drliki.MusicLi.R

@Composable
fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Tune,
        title = stringResource(R.string.equalizer_save_custom_preset_title),
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            if (name.isBlank()) {
                isError = true
            } else {
                onSave(name)
                onDismiss()
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.equalizer_save_custom_preset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isError = false
                },
                label = { Text(stringResource(R.string.equalizer_preset_name_label)) },
                singleLine = true,
                isError = isError,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (isError) {
                Text(
                    text = stringResource(R.string.error_name_empty),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RenamePresetDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }

    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Edit,
        title = stringResource(R.string.equalizer_rename_preset_title),
        confirmText = stringResource(R.string.action_rename),
        onConfirm = {
            if (name.isBlank()) {
                isError = true
            } else {
                onRename(name.trim())
                onDismiss()
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isError = false
                },
                label = { Text(stringResource(R.string.equalizer_preset_name_label)) },
                singleLine = true,
                isError = isError,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (isError) {
                Text(
                    text = stringResource(R.string.error_name_empty),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
