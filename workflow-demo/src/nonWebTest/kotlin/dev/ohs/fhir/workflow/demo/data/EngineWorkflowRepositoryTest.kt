package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineWorkflowRepositoryTest {
  @Test
  fun `create then read`() = runTest {
    val repo = EngineWorkflowRepository(fhirEngine())
    repo.create(
      MedicationRequest(
        id = "mr-1",
        status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
        intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
        medication = MedicationRequest.Medication.CodeableConcept(CodeableConcept()),
        subject = Reference(reference = FhirString(value = "Patient/p1")),
      ),
    )
    assertEquals("mr-1", repo.read("MedicationRequest", "mr-1")?.id)
  }
}
