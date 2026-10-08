package com.egrmeister.roomresetmini.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.egrmeister.roomresetmini.R
import com.egrmeister.roomresetmini.domain.progress.ZoneState
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import java.util.Locale

@DrawableRes
fun ZoneType.iconRes(): Int = when (this) {
    ZoneType.DESK -> R.drawable.ic_zone_desk
    ZoneType.FLOOR -> R.drawable.ic_zone_floor
    ZoneType.CLOTHES -> R.drawable.ic_zone_clothes
    ZoneType.TRASH -> R.drawable.ic_zone_trash
    ZoneType.SHELF -> R.drawable.ic_zone_shelf
}

data class ZoneColors(val container: Color, val border: Color)

fun zoneColors(state: ZoneState): ZoneColors = when (state) {
    ZoneState.NO_TASKS -> ZoneColors(RoomColors.SandLight, RoomColors.Wall)
    ZoneState.NOT_STARTED -> ZoneColors(RoomColors.Sand, RoomColors.SandBorder)
    ZoneState.IN_PROGRESS -> ZoneColors(RoomColors.PaleBlue, RoomColors.BlueBorder)
    ZoneState.DONE -> ZoneColors(RoomColors.Sage, RoomColors.SageBorder)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.semantics { heading() },
    )
}

/** Done marker: a checkmark plus text, never colour alone. */
@Composable
fun CheckBadge(done: Boolean, modifier: Modifier = Modifier, doneText: String = "Done", openText: String = "Not done") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = if (done) RoomColors.SageDeep else Color.Transparent,
                border = if (done) null else androidx.compose.foundation.BorderStroke(1.5.dp, RoomColors.CharcoalMuted),
                modifier = Modifier.size(20.dp),
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (done) doneText else openText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

fun formatMinutes(millis: Long): String = "${millis / 60_000L} min"

/** mm:ss, rounding up so the display reaches 0:00 exactly at expiry. */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
