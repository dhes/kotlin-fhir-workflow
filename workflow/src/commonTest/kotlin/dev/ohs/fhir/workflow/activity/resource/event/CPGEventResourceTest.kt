package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import kotlin.test.Test
import kotlin.test.assertEquals

class CPGEventResourceTest {
  @Test
  fun `communication event is created from a communication request in preparation`() {
    val request = CPGCommunicationRequest(
      CommunicationRequest(id = "cr-1", status = Enumeration(value = CommunicationRequest.RequestStatus.Active)),
    ).apply { setIntent(Intent.PROPOSAL) }

    val event = CPGCommunicationEvent.from(request)
    assertEquals(EventStatus.PREPARATION, event.getStatus())
  }
}
