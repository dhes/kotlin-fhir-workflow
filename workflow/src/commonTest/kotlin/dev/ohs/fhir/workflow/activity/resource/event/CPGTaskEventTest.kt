package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CPGTaskEventTest {
  private fun taskRequest() = CPGTaskRequest(
    Task(
      id = "task-1",
      status = Enumeration(value = Task.TaskStatus.Requested),
      intent = Enumeration(value = Task.TaskIntent.Order),
    ),
  ).apply { setIntent(Intent.ORDER) }

  @Test
  fun shouldCreateTaskEventInPreparationWhenFromRequest() {
    val event = CPGTaskEvent.from(taskRequest())
    assertEquals(EventStatus.PREPARATION, event.getStatus())
    assertEquals("Task/task-1", event.getBasedOn()?.reference?.value)
  }

  @Test
  fun shouldResolveToTaskEventWhenTaskRequestOrResource() {
    assertTrue(CPGEventResource.from(taskRequest(), "CPGTaskEvent") is CPGTaskEvent)
    assertTrue(
      CPGEventResource.of(
        Task(
          id = "t",
          status = Enumeration(value = Task.TaskStatus.Ready),
          intent = Enumeration(value = Task.TaskIntent.Order),
        ),
      ) is CPGTaskEvent,
    )
  }
}
