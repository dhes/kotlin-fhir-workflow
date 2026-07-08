package dev.ohs.fhir.workflow.demo.flow

import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.uuid.Uuid

/** Builds the seed [CPGMedicationRequest] proposal the demo's ActivityFlow starts from. */
object ProposalFactory {
  private const val CPG_MEDICATION_REQUEST_PROFILE =
    "http://hl7.org/fhir/uv/cpg/StructureDefinition/cpg-medicationrequest"

  fun medicationProposal(patientId: String): CPGMedicationRequest = CPGMedicationRequest(
    MedicationRequest(
      id = Uuid.random().toString(),
      meta = Meta(profile = listOf(Canonical(value = CPG_MEDICATION_REQUEST_PROFILE))),
      status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
      intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
      medication = MedicationRequest.Medication.CodeableConcept(
        CodeableConcept(text = FhirString(value = "Apple, daily")),
      ),
      subject = Reference(reference = FhirString(value = "Patient/$patientId")),
    ),
  )
}
