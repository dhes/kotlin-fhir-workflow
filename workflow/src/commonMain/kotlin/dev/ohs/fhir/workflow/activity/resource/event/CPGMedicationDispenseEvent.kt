package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationDispense
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.uuid.Uuid

class CPGMedicationDispenseEvent(resource: MedicationDispense) :
  CPGOrderMedicationEvent<MedicationDispense>(MedicationDispenseEventMapper) {

  override var resource: MedicationDispense = resource

  override fun setStatus(status: EventStatus, reason: String?) {
    resource = resource.copy(
      status = Enumeration(
        value = MedicationDispense.MedicationDispenseStatusCodes.fromCode(
          mapper.mapStatusToCode(status) ?: "unknown",
        ),
      ),
      statusReason = reason?.let {
        MedicationDispense.StatusReason.CodeableConcept(CodeableConcept(coding = listOf(Coding(code = Code(value = it)))))
      },
    )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()
  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(authorizingPrescription = resource.authorizingPrescription + reference)
  }
  override fun getBasedOn(): Reference? = resource.authorizingPrescription.lastOrNull()
  override fun copy(): CPGEventResource<MedicationDispense> = CPGMedicationDispenseEvent(resource.copy())

  companion object {
    fun from(request: CPGMedicationRequest): CPGMedicationDispenseEvent {
      val src = request.resource
      return CPGMedicationDispenseEvent(
        MedicationDispense(
          id = Uuid.random().toString(),
          status = Enumeration(value = MedicationDispense.MedicationDispenseStatusCodes.Preparation),
          medication = medReqToDispenseMedication(src.medication),
          category = src.category.firstOrNull(),
          subject = src.subject,
          context = src.encounter,
          note = src.note,
          dosageInstruction = src.dosageInstruction,
        ),
      )
    }

    // MedicationDispense.medication is required (1..1); fall back to an empty CodeableConcept
    // when the source request has none so the resulting resource stays valid.
    private fun medReqToDispenseMedication(
      medication: MedicationRequest.Medication?,
    ): MedicationDispense.Medication = when (medication) {
      is MedicationRequest.Medication.CodeableConcept -> MedicationDispense.Medication.CodeableConcept(medication.value)
      is MedicationRequest.Medication.Reference -> MedicationDispense.Medication.Reference(medication.value)
      null -> MedicationDispense.Medication.CodeableConcept(CodeableConcept())
    }
  }
}

private object MedicationDispenseEventMapper : EventStatusCodeMapperImpl() {
  override fun mapCodeToStatus(code: String?): EventStatus =
    if (code == "cancelled") EventStatus.NOTDONE else super.mapCodeToStatus(code)
  override fun mapStatusToCode(status: EventStatus): String? =
    if (status == EventStatus.NOTDONE) "cancelled" else super.mapStatusToCode(status)
}
