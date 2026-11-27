package com.drliki.MusicLi.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.drliki.MusicLi.R

/**
 * Asks before deleting the playlists picked in a multi-selection, which can't be undone.
 * A single playlist reuses the wording of the playlist screen's own delete dialog.
 */
@Composable
fun DeletePlaylistsConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (count == 1) {
        stringResource(R.string.presentation_batch_b_delete_playlist_confirm_title)
    } else {
        pluralStringResource(R.plurals.delete_playlists_confirmation_title, count, count)
    }
    val body = if (count == 1) {
        stringResource(R.string.presentation_batch_b_delete_playlist_confirm_body)
    } else {
        pluralStringResource(R.plurals.delete_playlists_confirmation_body, count, count)
    }

    MusicLiDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.DeleteForever,
        isDestructive = true,
        title = title,
        confirmText = stringResource(R.string.delete_action),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
