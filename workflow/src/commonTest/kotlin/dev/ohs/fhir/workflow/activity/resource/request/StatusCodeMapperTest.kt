package dev.ohs.fhir.workflow.activity.resource.request

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusCodeMapperTest {
  private val mapper = StatusCodeMapperImpl()

  @Test
  fun `maps code to status and back`() {
    assertEquals(Status.ACTIVE, mapper.mapCodeToStatus("active"))
    assertEquals("on-hold", mapper.mapStatusToCode(Status.ONHOLD))
  }

  @Test
  fun `intent of code`() {
    assertEquals(Intent.PLAN, Intent.of("plan"))
    assertEquals("order", Intent.ORDER.code)
  }
}
