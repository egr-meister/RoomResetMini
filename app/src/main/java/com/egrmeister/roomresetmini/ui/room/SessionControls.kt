package com.egrmeister.roomresetmini.ui.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.egrmeister.roomresetmini.R
import com.egrmeister.roomresetmini.domain.progress.SessionProgress
import com.egrmeister.roomresetmini.domain.sessions.Durations
import com.egrmeister.roomresetmini.domain.timer.TimerStatus
import com.egrmeister.roomresetmini.ui.theme.RoomColors
import com.egrmeister.roomresetmini.ui.theme.formatClock

/** Before a session: duration choices + Start Reset. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StartControls(
    durationMinutes: Int,
    enabled: Boolean,
    onDuration: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Durations.MINUTES.forEach { minutes ->
                FilterChip(
                    selected = durationMinutes == minutes,
                    onClick = { onDuration(minutes) },
                    label = { Text("$minutes min") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Reset")
        }
    }
}

/** During a session: compact strip with remaining time, Pause/Resume and Finish. */
@Composable
fun TimerStrip(
    status: TimerStatus,
    remainingMillis: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = RoomColors.PaleBlue,
        border = BorderStroke(1.dp, RoomColors.BlueBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (main, sub) = when (status) {
                TimerStatus.RUNNING -> formatClock(remainingMillis) to "remaining"
                TimerStatus.PAUSED -> formatClock(remainingMillis) to "paused"
                TimerStatus.EXPIRED -> "0:00" to "time is up"
                TimerStatus.OFF -> "Extra time" to "no timer"
            }
            // Not a live region: the countdown is never announced tick by tick.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = timerDescription(status, remainingMillis) },
            ) {
                Text(main, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (status) {
                TimerStatus.RUNNING -> OutlinedButton(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pause")
                }
                TimerStatus.PAUSED -> OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Resume")
                }
                TimerStatus.EXPIRED, TimerStatus.OFF -> Unit
            }
            FilledTonalButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Finish")
            }
        }
    }
}

private fun timerDescription(status: TimerStatus, remainingMillis: Long): String {
    val totalSeconds = (remainingMillis + 999L) / 1000L
    val time = "${totalSeconds / 60} minutes ${totalSeconds % 60} seconds"
    return when (status) {
        TimerStatus.RUNNING -> "Timer running, $time remaining"
        TimerStatus.PAUSED -> "Timer paused, $time remaining"
        TimerStatus.EXPIRED -> "Time is up"
        TimerStatus.OFF -> "Extra time, no timer"
    }
}

@Composable
fun SessionProgressLine(progress: SessionProgress, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${progress.completedTasks} of ${progress.totalTasks} tasks checked · " +
                "${progress.completedZones} of ${progress.nonEmptyZones} zones done",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = RoomColors.SageDeep,
            trackColor = RoomColors.Sand,
        )
    }
}

/** Shown at expiry: progress is preserved; the user chooses what happens next. */
@Composable
fun ExpiredBanner(onSave: () -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        text = "Time is up. Save your progress or keep going.",
        modifier = modifier,
    ) {
        Button(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save session") }
        OutlinedButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) { Text("Continue without timer") }
    }
}

@Composable
fun RestartBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    NoticeCard(
        text = "The timer was paused after a restart. Your tasks are as you left them. Tap Resume to continue.",
        modifier = modifier,
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RoomColors.SandBorder),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { actions() }
        }
    }
}
