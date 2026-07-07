package dev.ohs.fhir.workflow.activity.resource.request

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import kotlin.test.Test
import kotlin.test.assertEquals

class CPGRequestResourceTest {
  @Test
  fun `communication request status and intent via extension round trip`() {
    val cr = CommunicationRequest(
      id = "cr-1",
      status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
    )
    val wrapped = CPGRequestResource.of(cr)
    wrapped.setIntent(Intent.PROPOSAL)
    assertEquals(Intent.PROPOSAL, wrapped.getIntent())
    assertEquals(Status.ACTIVE, wrapped.getStatus())

    wrapped.setStatus(Status.ONHOLD)
    assertEquals(Status.ONHOLD, wrapped.getStatus())
  }

  @Test
  fun `copy with new id sets basedOn to parent`() {
    val cr = CommunicationRequest(
      id = "cr-1",
      status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
    )
    val parent = CPGRequestResource.of(cr).apply { setIntent(Intent.PROPOSAL) }
    val child = parent.copy(id = "cr-1-plan", status = Status.DRAFT, intent = Intent.PLAN)
    assertEquals("CommunicationRequest/cr-1", child.getBasedOn()?.reference?.value)
    assertEquals(Intent.PLAN, child.getIntent())
  }
}
