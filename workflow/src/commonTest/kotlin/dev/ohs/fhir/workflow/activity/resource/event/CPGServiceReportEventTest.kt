package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CPGServiceReportEventTest {
  private fun serviceRequest() = CPGServiceRequest(
    ServiceRequest(
      id = "sr-1",
      status = Enumeration(value = ServiceRequest.RequestStatus.Active),
      intent = Enumeration(value = ServiceRequest.RequestIntent.Order),
      code = CodeableConcept(coding = listOf(Coding(code = Code(value = "CBC")))),
      subject = Reference(reference = FhirString(value = "Patient/p1")),
    ),
  ).apply { setIntent(Intent.ORDER) }

  @Test
  fun shouldCreateReportEventFromServiceRequestBasedOnRequest() {
    val event = CPGServiceReportEvent.from(serviceRequest())
    assertEquals(EventStatus.PREPARATION, event.getStatus())
    assertEquals("ServiceRequest/sr-1", event.getBasedOn()?.reference?.value)
    assertEquals("CBC", event.resource.code.coding.firstOrNull()?.code?.value)
  }

  @Test
  fun shouldResolveToServiceReportEventWhenServiceRequestOrReport() {
    assertTrue(CPGEventResource.from(serviceRequest(), "CPGServiceReportEvent") is CPGServiceReportEvent)
  }
}
