package com.egrmeister.roomresetmini.domain

import com.egrmeister.roomresetmini.domain.progress.Progress
import com.egrmeister.roomresetmini.domain.progress.ZoneState
import com.egrmeister.roomresetmini.domain.sessions.ResultKind
import com.egrmeister.roomresetmini.domain.sessions.SessionSnapshot
import com.egrmeister.roomresetmini.domain.sessions.SessionTask
import com.egrmeister.roomresetmini.domain.sessions.savedMessage
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.StarterTemplates
import com.egrmeister.roomresetmini.domain.templates.TaskRef
import com.egrmeister.roomresetmini.domain.templates.TemplateDraft
import com.egrmeister.roomresetmini.domain.templates.TemplateNames
import com.egrmeister.roomresetmini.domain.templates.TemplateTask
import com.egrmeister.roomresetmini.domain.templates.TemplateValidator
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAndTemplateTest {

    private fun task(id: Long, zone: ZoneType, done: Boolean = false) =
        SessionTask(id = id, zone = zone, label = "Task $id", order = id.toInt(), completed = done)

    @Test
    fun taskCompletionAndUndoUpdateZoneState() {
        var tasks = listOf(task(1, ZoneType.DESK), task(2, ZoneType.DESK))
        assertEquals(ZoneState.NOT_STARTED, Progress.zone(ZoneType.DESK, tasks).state)

        tasks = tasks.map { if (it.id == 1L) it.copy(completed = true) else it }
        assertEquals(ZoneState.IN_PROGRESS, Progress.zone(ZoneType.DESK, tasks).state)

        tasks = tasks.map { it.copy(completed = true) }
        assertEquals(ZoneState.DONE, Progress.zone(ZoneType.DESK, tasks).state)

        // Undo one task: zone returns to In progress, not Done.
        tasks = tasks.map { if (it.id == 2L) it.copy(completed = false) else it }
        assertEquals(ZoneState.IN_PROGRESS, Progress.zone(ZoneType.DESK, tasks).state)

        // Undo the last checked task: back to Not started.
        tasks = tasks.map { it.copy(completed = false) }
        assertEquals(ZoneState.NOT_STARTED, Progress.zone(ZoneType.DESK, tasks).state)
    }

    @Test
    fun emptyZonesAreExcludedFromTotals() {
        val tasks = listOf(task(1, ZoneType.DESK, done = true), task(2, ZoneType.FLOOR))
        val progress = Progress.session(tasks)
        assertEquals(2, progress.nonEmptyZones)
        assertEquals(1, progress.completedZones)
        assertEquals(2, progress.totalTasks)
        assertEquals(ZoneState.NO_TASKS, Progress.zone(ZoneType.SHELF, tasks).state)
        assertEquals(0, Progress.zone(ZoneType.SHELF, tasks).total)
    }

    @Test
    fun progressIsTaskBasedNotZoneWeighted() {
        // Desk 1/1 done, Floor 0/3: zone-weighted would be 50 %, task-based must be 25 %.
        val tasks = listOf(
            task(1, ZoneType.DESK, done = true),
            task(2, ZoneType.FLOOR),
            task(3, ZoneType.FLOOR),
            task(4, ZoneType.FLOOR),
        )
        val progress = Progress.session(tasks)
        assertEquals(0.25f, progress.fraction, 0.0001f)
        assertEquals(1, progress.completedTasks)
        assertEquals(3, progress.remainingTasks)
        assertFalse(progress.allChecked)
    }

    @Test
    fun snapshotIsIndependentOfLaterTemplateEdits() {
        val labels = mutableListOf(
            TemplateTask(1, ZoneType.DESK, "Clear the surface", 1),
            TemplateTask(2, ZoneType.DESK, "Put stationery away", 0),
            TemplateTask(3, ZoneType.TRASH, "Collect ordinary rubbish", 0),
        )
        val template = RoomTemplate(7, "Bedroom", 0, labels.toList())
        val snapshot = SessionSnapshot.from(template, 10 * 60_000L)

        // Edit / delete the template afterwards.
        val edited = template.copy(name = "Renamed", tasks = template.tasks.drop(1))
        labels.clear()

        assertEquals("Bedroom", snapshot.templateName)
        assertEquals(3, snapshot.tasks.size)
        assertEquals(listOf("Put stationery away", "Clear the surface"), snapshot.tasks.filter { it.zone == ZoneType.DESK }.map { it.label })
        assertEquals(listOf(0, 1), snapshot.tasks.filter { it.zone == ZoneType.DESK }.map { it.order })
        assertEquals(600_000L, snapshot.durationMillis)
        assertEquals("Renamed", edited.name)
    }

    @Test
    fun resultKindsNeverFabricateCompletion() {
        assertEquals(ResultKind.ALL_CHECKED, ResultKind.of(3, 3))
        assertEquals(ResultKind.PARTIAL, ResultKind.of(1, 3))
        assertEquals(ResultKind.NOTHING_CHECKED, ResultKind.of(0, 3))
        assertEquals("All tasks checked.", ResultKind.of(3, 3).savedMessage())
        assertEquals("Progress saved.", ResultKind.of(2, 3).savedMessage())
        assertEquals("Partial", ResultKind.NOTHING_CHECKED.label)
    }

    @Test
    fun validatorEnforcesNameTaskAndCountRules() {
        val ok = TemplateDraft.of("  Office  ", ZoneType.DESK to listOf("Stack papers"))
        assertTrue(TemplateValidator.validate(ok, listOf("Bedroom")).isValid)

        assertNotNull(TemplateValidator.validate(ok.copy(name = "   "), emptyList()).nameError)
        assertNotNull(TemplateValidator.validate(ok.copy(name = "x".repeat(31)), emptyList()).nameError)
        assertNull(TemplateValidator.validate(ok.copy(name = "x".repeat(30)), emptyList()).nameError)
        assertNotNull(TemplateValidator.validate(ok.copy(name = "bedroom"), listOf("Bedroom")).nameError)

        val noTasks = TemplateDraft.of("Empty")
        assertNotNull(TemplateValidator.validate(noTasks, emptyList()).formError)

        val blank = TemplateDraft.of("Blank", ZoneType.SHELF to listOf("Return books", "  "))
        assertEquals(setOf(TaskRef(ZoneType.SHELF, 1)), TemplateValidator.validate(blank, emptyList()).taskErrors.keys)

        val long = TemplateDraft.of("Long", ZoneType.SHELF to listOf("y".repeat(81)))
        assertFalse(TemplateValidator.validate(long, emptyList()).isValid)

        val tooMany = TemplateDraft.of("Many", ZoneType.FLOOR to List(11) { "Task $it" })
        assertNotNull(TemplateValidator.validate(tooMany, emptyList()).formError)
        val ten = TemplateDraft.of("Ten", ZoneType.FLOOR to List(10) { "Task $it" })
        assertTrue(TemplateValidator.validate(ten, emptyList()).isValid)
    }

    @Test
    fun uniqueNamesIgnoreCaseAndStayWithinLimit() {
        assertEquals("Bedroom", TemplateNames.unique("Bedroom", listOf("Study Space")))
        assertEquals("Bedroom 2", TemplateNames.unique("Bedroom", listOf("bedroom")))
        assertEquals("Bedroom 3", TemplateNames.unique("Bedroom", listOf("Bedroom", "Bedroom 2")))
        val long = "L".repeat(30)
        val unique = TemplateNames.unique(long, listOf(long))
        assertTrue(unique.length <= 30)
        assertTrue(unique.endsWith(" 2"))
    }

    @Test
    fun starterTemplatesMatchSpecAndAreValid() {
        assertEquals(listOf("Bedroom", "Study Space", "Living Room"), StarterTemplates.all.map { it.name })
        StarterTemplates.all.forEach { draft ->
            assertTrue(draft.name, TemplateValidator.validate(draft, emptyList()).isValid)
            ZoneType.entries.forEach { zone -> assertTrue(draft.labels(zone).isNotEmpty()) }
        }
        assertEquals(10, StarterTemplates.all.first().totalTasks)
    }
}
