package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Communication
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CPGCommunicationEventTest {
  @Test
  fun shouldCarryPriorityAndPayloadWhenCreatedFromRequest() {
    val request = CPGCommunicationRequest(
      CommunicationRequest(
        id = "cr-1",
        status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
        priority = Enumeration(value = CommunicationRequest.RequestPriority.Urgent),
        payload = listOf(
          CommunicationRequest.Payload(
            content = CommunicationRequest.Payload.Content.String(FhirString(value = "call the CHW")),
          ),
        ),
      ),
    )

    val event = CPGCommunicationEvent.from(request)

    assertEquals("urgent", event.resource.priority?.value?.getCode())
    val content = event.resource.payload.single().content
    assertEquals("call the CHW", (content as Communication.Payload.Content.String).value.value)
  }
}
