package com.drliki.MusicLi.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drliki.MusicLi.R

@Composable
fun AllFilesAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.FolderOpen,
        title = stringResource(id = R.string.all_files_access_title),
        confirmText = stringResource(id = R.string.grant_permission_button),
        onConfirm = onConfirm,
        dismissText = stringResource(id = R.string.cancel),
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(id = R.string.all_files_access_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
