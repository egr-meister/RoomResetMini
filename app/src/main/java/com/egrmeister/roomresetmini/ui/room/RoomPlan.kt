package com.egrmeister.roomresetmini.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.egrmeister.roomresetmini.domain.progress.ZoneProgress
import com.egrmeister.roomresetmini.domain.progress.ZoneState
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.theme.RoomColors
import com.egrmeister.roomresetmini.ui.theme.iconRes
import com.egrmeister.roomresetmini.ui.theme.zoneColors

/**
 * Simplified top-down room plan with five labelled regions. On narrow screens, with large fonts,
 * or when the user picks "List view", the same zones are shown as a readable vertical list
 * (also the accessible equivalent of the illustration).
 */
@Composable
fun RoomPlan(
    zones: List<ZoneProgress>,
    listView: Boolean,
    reducedMotion: Boolean,
    onZone: (ZoneType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byZone = zones.associateBy { it.zone }
    fun p(zone: ZoneType) = byZone[zone] ?: ZoneProgress(zone, 0, 0)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.35f
        if (listView || narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneType.entries.forEach { zone ->
                    ZoneRegion(
                        progress = p(zone),
                        onClick = { onZone(zone) },
                        reducedMotion = reducedMotion,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        horizontal = true,
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(3.dp, RoomColors.Wall),
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(0.68f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Desk: a work surface near the upper edge.
                        ZoneRegion(
                            progress = p(ZoneType.DESK),
                            onClick = { onZone(ZoneType.DESK) },
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 84.dp),
                            decoration = DeskDecoration,
                        )
                        // Floor: the central open area.
                        ZoneRegion(
                            progress = p(ZoneType.FLOOR),
                            onClick = { onZone(ZoneType.FLOOR) },
                            reducedMotion = reducedMotion,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp),
                            decoration = RugDecoration,
                        )
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Shelf: a horizontal storage region.
                            ZoneRegion(
                                progress = p(ZoneType.SHELF),
                                onClick = { onZone(ZoneType.SHELF) },
                                reducedMotion = reducedMotion,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 84.dp),
                                decoration = ShelfDecoration,
                            )
                            // Trash: a small bin near the lower edge.
                            ZoneRegion(
                                progress = p(ZoneType.TRASH),
                                onClick = { onZone(ZoneType.TRASH) },
                                reducedMotion = reducedMotion,
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                                modifier = Modifier
                                    .width(104.dp)
                                    .fillMaxHeight()
                                    .heightIn(min = 84.dp),
                            )
                        }
                    }
                    // Clothes: a wardrobe-shaped side region.
                    ZoneRegion(
                        progress = p(ZoneType.CLOTHES),
                        onClick = { onZone(ZoneType.CLOTHES) },
                        reducedMotion = reducedMotion,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight(),
                        decoration = WardrobeDecoration,
                    )
                }
            }
        }
    }
}

@Composable
fun ZoneRegion(
    progress: ZoneProgress,
    onClick: () -> Unit,
    reducedMotion: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    decoration: (DrawScope.() -> Unit)? = null,
) {
    val colors = zoneColors(progress.state)
    val background by animateColorAsState(
        targetValue = colors.container,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 280),
        label = "zoneBackground",
    )
    val countText = if (progress.total == 0) "No tasks" else "${progress.completed}/${progress.total} tasks"
    val spoken = if (progress.total == 0) {
        "${progress.zone.label}. No tasks. Opens checklist."
    } else {
        "${progress.zone.label}. ${progress.completed} of ${progress.total} tasks. ${progress.state.label}. Opens checklist."
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, colors.border),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken
            stateDescription = progress.state.label
            role = Role.Button
        },
    ) {
        Box {
            if (decoration != null) {
                Spacer(
                    Modifier
                        .matchParentSize()
                        .drawBehind(decoration),
                )
            }
            if (horizontal) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ZoneHeader(progress.zone, Modifier.weight(1f))
                    Text(countText, style = MaterialTheme.typography.bodySmall)
                    ZoneStateLabel(progress.state, reducedMotion)
                }
            } else {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ZoneHeader(progress.zone)
                    Text(countText, style = MaterialTheme.typography.bodySmall)
                    ZoneStateLabel(progress.state, reducedMotion)
                }
            }
        }
    }
}

@Composable
private fun ZoneHeader(zone: ZoneType, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(zone.iconRes()), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(zone.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ZoneStateLabel(state: ZoneState, reducedMotion: Boolean) {
    val done = state == ZoneState.DONE
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (reducedMotion) {
            if (done) DoneCheck()
        } else {
            AnimatedVisibility(visible = done, enter = scaleIn() + fadeIn()) { DoneCheck() }
        }
        Text(
            state.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DoneCheck() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = RoomColors.SageDeep,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(16.dp),
    )
}

// ---- Muted sand "furniture" details drawn natively inside each region --------------------------

private val DeskDecoration: DrawScope.() -> Unit = {
    val leg = 6.dp.toPx()
    val inset = 14.dp.toPx()
    val color = RoomColors.Wall.copy(alpha = 0.6f)
    drawRect(color, topLeft = Offset(inset, size.height - leg), size = Size(leg, leg))
    drawRect(color, topLeft = Offset(size.width - inset - leg, size.height - leg), size = Size(leg, leg))
}

private val RugDecoration: DrawScope.() -> Unit = {
    val inset = 18.dp.toPx()
    if (size.width > inset * 2 && size.height > inset * 2) {
        drawRoundRect(
            color = RoomColors.Wall.copy(alpha = 0.22f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
    }
}

private val ShelfDecoration: DrawScope.() -> Unit = {
    val line = 2.dp.toPx()
    drawRect(
        RoomColors.Wall.copy(alpha = 0.5f),
        topLeft = Offset(0f, size.height - 8.dp.toPx()),
        size = Size(size.width, line),
    )
}

private val WardrobeDecoration: DrawScope.() -> Unit = {
    val line = 2.dp.toPx()
    val color = RoomColors.Wall.copy(alpha = 0.6f)
    val top = 8.dp.toPx()
    if (size.height > top * 2) {
        drawRect(color, topLeft = Offset(size.width / 2f - line / 2f, top), size = Size(line, size.height - top * 2))
    }
    val knob = 3.dp.toPx()
    drawCircle(color, radius = knob, center = Offset(size.width / 2f - 8.dp.toPx(), size.height * 0.62f))
    drawCircle(color, radius = knob, center = Offset(size.width / 2f + 8.dp.toPx(), size.height * 0.62f))
}
