package com.drliki.MusicLi.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drliki.MusicLi.ui.theme.RoundedSans

/**
 * Shared edge-to-edge-width Material 3 Expressive dialog container used across MusicLi.
 * Uses `usePlatformDefaultWidth = false` with consistent `12.dp` horizontal margins so popups
 * feel spacious and cohesive across phones and tablets while handling IME and system insets cleanly.
 */
@Composable
fun MusicLiDialogShell(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape: Shape = RoundedCornerShape(28.dp),
    horizontalMargin: Dp = 12.dp,
    maxWidth: Dp = 560.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalMargin, vertical = 16.dp)
                .widthIn(max = maxWidth),
            shape = shape,
            color = containerColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Unified MusicLi popup dialog with a tonal top icon badge, expressive title, scrollable body,
 * and standardized 50/50 side-by-side Outlined/Filled action buttons.
 */
@Composable
fun MusicLiDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    iconContainerColor: Color? = null,
    isDestructive: Boolean = false,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
    scrollable: Boolean = true,
    maxBodyHeight: Dp = 440.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedIconContainer = iconContainerColor ?: if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val resolvedIconTint = iconTint ?: if (isDestructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    MusicLiDialogShell(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = containerColor
    ) {
        if (icon != null || title != null || titleContent != null) {
            if (titleContent != null) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MusicLiDialogIconBadge(
                            icon = icon,
                            containerColor = resolvedIconContainer,
                            tint = resolvedIconTint
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                titleContent()
            } else {
                val centerHeader = icon != null
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (centerHeader) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    if (icon != null) {
                        MusicLiDialogIconBadge(
                            icon = icon,
                            containerColor = resolvedIconContainer,
                            tint = resolvedIconTint
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = RoundedSans,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = if (centerHeader) TextAlign.Center else TextAlign.Start
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        val bodyModifier = Modifier
            .fillMaxWidth()
            .then(if (maxBodyHeight != Dp.Unspecified) Modifier.heightIn(max = maxBodyHeight) else Modifier)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)

        Column(
            modifier = bodyModifier,
            content = content
        )

        val hasConfirm = confirmText != null && onConfirm != null
        val hasDismiss = dismissText != null
        val hasNeutral = neutralText != null && onNeutral != null

        if (hasConfirm || hasDismiss || hasNeutral) {
            Spacer(modifier = Modifier.height(20.dp))
            MusicLiDialogActions(
                confirmText = confirmText,
                onConfirm = onConfirm,
                confirmEnabled = confirmEnabled,
                isDestructive = isDestructive,
                dismissText = dismissText,
                onDismiss = onDismiss ?: onDismissRequest,
                neutralText = neutralText,
                onNeutral = onNeutral
            )
        }
    }
}

@Composable
fun MusicLiDialogIconBadge(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )
    }
}

@Composable
fun MusicLiDialogActions(
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    val hasConfirm = confirmText != null && onConfirm != null
    val hasDismiss = dismissText != null
    val hasNeutral = neutralText != null && onNeutral != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasNeutral) {
            TextButton(
                onClick = onNeutral!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = neutralText!!,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        when {
            hasConfirm && hasDismiss -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = dismissText!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onConfirm!!,
                        enabled = confirmEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = if (isDestructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(
                            text = confirmText!!,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            hasConfirm -> {
                Button(
                    onClick = onConfirm!!,
                    enabled = confirmEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (isDestructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(
                        text = confirmText!!,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            hasDismiss -> {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = dismissText!!,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
