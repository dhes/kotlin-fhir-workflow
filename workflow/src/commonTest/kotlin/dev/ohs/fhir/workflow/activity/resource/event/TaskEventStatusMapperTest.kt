package dev.ohs.fhir.workflow.activity.resource.event

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskEventStatusMapperTest {
  @Test
  fun `maps task codes to event statuses`() {
    assertEquals(EventStatus.PREPARATION, TaskEventStatusMapper.mapCodeToStatus("ready"))
    assertEquals(EventStatus.INPROGRESS, TaskEventStatusMapper.mapCodeToStatus("in-progress"))
    assertEquals(EventStatus.COMPLETED, TaskEventStatusMapper.mapCodeToStatus("completed"))
    assertEquals(EventStatus.ONHOLD, TaskEventStatusMapper.mapCodeToStatus("on-hold"))
    assertEquals(EventStatus.STOPPED, TaskEventStatusMapper.mapCodeToStatus("cancelled"))
    assertEquals(EventStatus.ENTEREDINERROR, TaskEventStatusMapper.mapCodeToStatus("entered-in-error"))
  }

  @Test
  fun `maps event statuses to task codes`() {
    assertEquals("ready", TaskEventStatusMapper.mapStatusToCode(EventStatus.PREPARATION))
    assertEquals("in-progress", TaskEventStatusMapper.mapStatusToCode(EventStatus.INPROGRESS))
    assertEquals("completed", TaskEventStatusMapper.mapStatusToCode(EventStatus.COMPLETED))
    assertEquals("on-hold", TaskEventStatusMapper.mapStatusToCode(EventStatus.ONHOLD))
    assertEquals("cancelled", TaskEventStatusMapper.mapStatusToCode(EventStatus.STOPPED))
  }
}
