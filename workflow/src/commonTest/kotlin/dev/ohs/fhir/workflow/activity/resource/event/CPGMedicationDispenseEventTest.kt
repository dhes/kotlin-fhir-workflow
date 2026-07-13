package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CPGMedicationDispenseEventTest {
  private fun category(text: String) = CodeableConcept(text = FhirString(value = text))

  private fun medicationRequest(categories: List<CodeableConcept>) =
    CPGMedicationRequest(
      MedicationRequest(
        id = "mr-1",
        status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
        intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Order),
        medication = MedicationRequest.Medication.CodeableConcept(category("amoxicillin")),
        subject = Reference(reference = FhirString(value = "Patient/p1")),
        category = categories,
      ),
    )

  @Test
  fun shouldOmitCategoryWhenRequestHasMultiple() {
    val event = CPGMedicationDispenseEvent.from(medicationRequest(listOf(category("a"), category("b"))))
    assertNull(event.resource.category)
  }

  @Test
  fun shouldKeepCategoryWhenRequestHasExactlyOne() {
    val event = CPGMedicationDispenseEvent.from(medicationRequest(listOf(category("vaccine"))))
    assertEquals("vaccine", event.resource.category?.text?.value)
  }
}
