package dev.ohs.fhir.workflow.demo.flow

import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGMedicationDispenseEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.repository.WorkflowRepository

/** A single phase's rendering in the demo UI. */
data class PhaseCard(val name: String, val details: String, val isActive: Boolean)

/**
 * Owns the demo's [ActivityFlow] lifecycle: seeds a medication proposal, walks it through
 * plan/order/perform, and renders each phase's resource for display.
 */
class ActivityFlowDemoModel(private val repository: WorkflowRepository) {
  private var activityFlow: ActivityFlow<CPGMedicationRequest, CPGEventResource<*>>? = null
  private var handler: ActivityHandler? = null
  private var currentPhaseName: Phase.PhaseName? = null

  private var proposal: CPGMedicationRequest? = null
  private var plan: CPGMedicationRequest? = null
  private var order: CPGMedicationRequest? = null
  private var event: CPGMedicationDispenseEvent? = null

  suspend fun createProposal(patientId: String) {
    val created = ProposalFactory.medicationProposal(patientId)
    repository.create(created.resource)

    activityFlow = ActivityFlow.of(repository, created)
    handler = ActivityHandler(requireNotNull(activityFlow))
    currentPhaseName = Phase.PhaseName.PROPOSAL

    proposal = created
    plan = null
    order = null
    event = null
  }

  suspend fun advance() {
    val handler = requireNotNull(handler) { "Call createProposal before advancing." }

    when (currentPhaseName) {
      Phase.PhaseName.PROPOSAL -> {
        handler.prepareAndInitiatePlan().getOrThrow()
        currentPhaseName = Phase.PhaseName.PLAN
        plan = currentRequestResource()
      }
      Phase.PhaseName.PLAN -> {
        handler.prepareAndInitiateOrder().getOrThrow()
        currentPhaseName = Phase.PhaseName.ORDER
        order = currentRequestResource()
      }
      Phase.PhaseName.ORDER -> {
        handler.prepareAndInitiatePerform().getOrThrow()
        currentPhaseName = Phase.PhaseName.PERFORM
        event = currentEventResource()
      }
      Phase.PhaseName.PERFORM, null -> Unit
    }

    // Advancing a phase completes the resource it was based on (e.g. initiating the order
    // completes the plan), so refresh every request resource we already know about from the
    // repository rather than relying on stale in-memory copies.
    proposal = proposal?.let { refresh(it) }
    plan = plan?.let { refresh(it) }
    order = order?.let { refresh(it) }
  }

  fun phaseCards(): List<PhaseCard> = listOf(
    PhaseCard("PROPOSAL", requestDetails(proposal), currentPhaseName == Phase.PhaseName.PROPOSAL),
    PhaseCard("PLAN", requestDetails(plan), currentPhaseName == Phase.PhaseName.PLAN),
    PhaseCard("ORDER", requestDetails(order), currentPhaseName == Phase.PhaseName.ORDER),
    PhaseCard("PERFORM", eventDetails(event), currentPhaseName == Phase.PhaseName.PERFORM),
  )

  suspend fun restart() {
    activityFlow = null
    handler = null
    currentPhaseName = null
    proposal = null
    plan = null
    order = null
    event = null
  }

  @Suppress("UNCHECKED_CAST")
  private fun currentRequestResource(): CPGMedicationRequest? =
    (activityFlow?.getCurrentPhase() as? Phase.RequestPhase<*>)?.getRequestResource() as? CPGMedicationRequest

  @Suppress("UNCHECKED_CAST")
  private fun currentEventResource(): CPGMedicationDispenseEvent? =
    (activityFlow?.getCurrentPhase() as? Phase.EventPhase<*>)?.getEventResource() as? CPGMedicationDispenseEvent

  private suspend fun refresh(request: CPGMedicationRequest): CPGMedicationRequest {
    val id = request.logicalId ?: return request
    val stored = repository.read(request.resourceType, id) as? MedicationRequest ?: return request
    return CPGMedicationRequest(stored)
  }

  private fun requestDetails(request: CPGMedicationRequest?): String {
    if (request == null) return "—"
    return listOf(
      "ID: ${request.logicalId}",
      "Intent: ${request.getIntent().code}",
      "Status: ${request.getStatus()}",
      "BasedOn: ${request.getBasedOn()?.reference?.value ?: "—"}",
    ).joinToString("\n")
  }

  private fun eventDetails(event: CPGMedicationDispenseEvent?): String {
    if (event == null) return "—"
    return listOf(
      "ID: ${event.logicalId}",
      "Status: ${event.getStatus()}",
      "BasedOn: ${event.getBasedOn()?.reference?.value ?: "—"}",
    ).joinToString("\n")
  }
}
