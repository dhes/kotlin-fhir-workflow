/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.chw.demo

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.event.CPGMedicationDispenseEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Status
import kotlin.uuid.Uuid

/**
 * Runs one vaccine administration through the CPG activity lifecycle using the :workflow
 * module's ActivityFlow: proposal -> plan -> order -> perform -> completed.
 *
 * The request side uses CPGMedicationRequest with the vaccine as the requested medication —
 * :workflow's activity registry has no immunization activity yet (its CPG resource
 * hierarchies are sealed), and this mirrors how :workflow-demo drives its medication cycle.
 * The caller records the resulting Immunization on the patient's chart when the perform
 * phase completes.
 */
class ImmunizationTaskFlow {

  enum class Step(val label: String) {
    PROPOSAL("Proposal created"),
    PLAN("Plan initiated"),
    ORDER("Order initiated"),
    PERFORM("Administration recorded"),
    COMPLETED("Task completed"),
  }

  private val repository = InMemoryRepository()

  /** Walks all phases for one dose; [onStep] fires after each transition lands. */
  suspend fun administer(patientId: String, doseLabel: String, onStep: suspend (Step) -> Unit) {
    val proposal =
      CPGMedicationRequest(
        MedicationRequest(
          id = Uuid.random().toString(),
          status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
          intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
          medication =
            MedicationRequest.Medication.CodeableConcept(
              CodeableConcept(
                coding =
                  listOf(
                    Coding(
                      system = Uri(value = "http://id.who.int/icd/release/11/mms"),
                      code = Code(value = "XM28X5"),
                      display = FhirString(value = "Measles-containing vaccine"),
                    )
                  ),
                text = FhirString(value = doseLabel),
              )
            ),
          subject = Reference(reference = FhirString(value = "Patient/$patientId")),
        )
      )
    repository.create(proposal.resource)
    onStep(Step.PROPOSAL)

    val flow = ActivityFlow.of(repository, proposal)

    activateCurrentRequest(flow)
    flow.initiatePlan(flow.preparePlan().getOrThrow()).getOrThrow()
    onStep(Step.PLAN)

    activateCurrentRequest(flow)
    flow.initiateOrder(flow.prepareOrder().getOrThrow()).getOrThrow()
    onStep(Step.ORDER)

    activateCurrentRequest(flow)
    val preparedEvent =
      flow.preparePerform<CPGMedicationDispenseEvent>("CPGMedicationDispenseEvent").getOrThrow()
    val performPhase = flow.initiatePerform(preparedEvent).getOrThrow()
    onStep(Step.PERFORM)

    performPhase.start().getOrThrow()
    performPhase.complete().getOrThrow()
    onStep(Step.COMPLETED)
  }

  /** Every phase's `prepare` requires the current request to be ACTIVE first. */
  @Suppress("UNCHECKED_CAST")
  private suspend fun activateCurrentRequest(
    flow: ActivityFlow<CPGMedicationRequest, *>
  ) {
    val currentPhase = flow.getCurrentPhase() as Phase.RequestPhase<CPGRequestResource<*>>
    currentPhase
      .update(currentPhase.getRequestResource().apply { setStatus(Status.ACTIVE) })
      .getOrThrow()
  }

  /** Minimal in-memory WorkflowRepository; the flow only needs read/create/update here. */
  private class InMemoryRepository : WorkflowRepository {
    private val resources = mutableMapOf<String, Resource>()

    private fun typeOf(resource: Resource) =
      resource::class.simpleName ?: error("Unable to determine resource type")

    override suspend fun read(type: String, id: String): Resource? = resources["$type/$id"]

    override suspend fun create(resource: Resource): String {
      val id = requireNotNull(resource.id) { "Resource must have an id to be created" }
      resources["${typeOf(resource)}/$id"] = resource
      return id
    }

    override suspend fun update(resource: Resource) {
      val id = requireNotNull(resource.id) { "Resource must have an id to be updated" }
      resources["${typeOf(resource)}/$id"] = resource
    }

    override suspend fun delete(type: String, id: String) {
      resources.remove("$type/$id")
    }

    override suspend fun searchByReferenceParam(
      type: String,
      param: String,
      referenceValue: String,
    ): List<Resource> = emptyList()

    override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
      emptyList()
  }
}
