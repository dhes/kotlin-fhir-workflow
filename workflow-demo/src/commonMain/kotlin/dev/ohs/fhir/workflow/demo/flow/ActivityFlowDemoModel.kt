package dev.ohs.fhir.workflow.demo.flow

import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGMedicationDispenseEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.repository.WorkflowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** The phases the demo walks through, in order. [NONE] means the flow has run to completion. */
enum class FlowPhase {
  INITIALIZE,
  PROPOSAL,
  PLAN,
  ORDER,
  PERFORM,
  NONE,
}

/** A single phase's rendering in the demo UI. */
data class PhaseCard(val phase: FlowPhase, val details: String, val isActive: Boolean)

/**
 * Owns the demo's [ActivityFlow] lifecycle: installs the knowledge artifacts, generates a proposal
 * by running `PlanDefinition/$apply` over them, walks it through plan/order/perform, and renders
 * each phase's resource for display.
 */
class ActivityFlowDemoModel(
  private val repository: WorkflowRepository,
  private val configuration: DemoConfiguration = MEDICATION_DISPENSE,
  private val proposalHandler: ProposalCreationHandler = ProposalCreationHandler(repository),
) {
  private var activityFlow: ActivityFlow<CPGMedicationRequest, CPGEventResource<*>>? = null
  private var handler: ActivityHandler? = null

  private var proposal: CPGMedicationRequest? = null
  private var plan: CPGMedicationRequest? = null
  private var order: CPGMedicationRequest? = null
  private var event: CPGMedicationDispenseEvent? = null

  private val _phase = MutableStateFlow(FlowPhase.INITIALIZE)
  val phase: StateFlow<FlowPhase> = _phase.asStateFlow()

  private val _progress = MutableStateFlow(false)
  val progress: StateFlow<Boolean> = _progress.asStateFlow()

  private val _cards = MutableStateFlow(phaseCards())
  val cards: StateFlow<List<PhaseCard>> = _cards.asStateFlow()

  /** Whether the knowledge artifacts are installed, i.e. whether Initialize still has work to do. */
  val initialized: StateFlow<Boolean>
    get() = _initialized.asStateFlow()

  private val _initialized = MutableStateFlow(false)

  /**
   * Picks up where a previous run left off: the repository may already hold the knowledge artifacts,
   * and a flow left half-finished is reconstructed from the requests it persisted, so the demo
   * resumes at the phase it was on rather than starting over.
   */
  suspend fun refresh() = withProgress {
    val installed = proposalHandler.checkInstalledDependencies(configuration)
    _initialized.value = installed
    if (!installed) {
      _phase.value = FlowPhase.INITIALIZE
      return@withProgress
    }
    _phase.value = resumeFlow() ?: FlowPhase.PROPOSAL
  }

  /**
   * Rebuilds the flow from the patient's persisted requests and returns the phase it left off at, or
   * null when there is nothing to resume.
   */
  @Suppress("UNCHECKED_CAST")
  private suspend fun resumeFlow(): FlowPhase? {
    val resumed = ActivityFlow.of(repository, configuration.patientId)
      .firstOrNull() as? ActivityFlow<CPGMedicationRequest, CPGEventResource<*>>
      ?: return null

    activityFlow = resumed
    handler = ActivityHandler(resumed)

    resumed.getPreviousPhases().forEach { record(it.getPhaseName(), it.getRequestResource()) }
    when (val current = resumed.getCurrentPhase()) {
      is Phase.EventPhase<*> -> event = current.getEventResource() as? CPGMedicationDispenseEvent
      is Phase.RequestPhase<*> -> record(current.getPhaseName(), current.getRequestResource())
      else -> Unit
    }

    // The phase to run next is the first one the flow has no resource for, as upstream does.
    return when {
      proposal == null -> FlowPhase.PROPOSAL
      plan == null -> FlowPhase.PLAN
      order == null -> FlowPhase.ORDER
      event == null -> FlowPhase.PERFORM
      else -> FlowPhase.NONE
    }
  }

  private fun record(phase: Phase.PhaseName, request: CPGRequestResource<*>?) {
    val medicationRequest = request as? CPGMedicationRequest ?: return
    when (phase) {
      Phase.PhaseName.PROPOSAL -> proposal = medicationRequest
      Phase.PhaseName.PLAN -> plan = medicationRequest
      Phase.PhaseName.ORDER -> order = medicationRequest
      else -> Unit
    }
  }

  suspend fun installDependencies() = withProgress {
    proposalHandler.installDependencies(configuration)
    _initialized.value = true
    _phase.value = FlowPhase.PROPOSAL
  }

  /** Runs the given phase; only the currently active phase is startable from the UI. */
  suspend fun start(phase: FlowPhase) = withProgress {
    when (phase) {
      FlowPhase.INITIALIZE -> {
        proposalHandler.installDependencies(configuration)
        _initialized.value = true
        _phase.value = FlowPhase.PROPOSAL
      }
      FlowPhase.PROPOSAL -> createProposal()
      FlowPhase.PLAN -> advance(FlowPhase.ORDER) { requireHandler().prepareAndInitiatePlan() }
      FlowPhase.ORDER -> advance(FlowPhase.PERFORM) { requireHandler().prepareAndInitiateOrder() }
      FlowPhase.PERFORM -> advance(FlowPhase.NONE) { requireHandler().prepareAndInitiatePerform() }
      FlowPhase.NONE -> Unit
    }
  }

  /**
   * Abandons the flow and starts over, deleting the resources it created: they are requests against
   * the patient, and the plan's applicability condition reads the patient's requests when deciding
   * whether to propose again.
   */
  suspend fun restart() = withProgress {
    listOfNotNull(proposal, plan, order).forEach { request ->
      request.logicalId?.let { repository.delete(request.resourceType, it) }
    }
    event?.let { dispense -> dispense.logicalId?.let { repository.delete(dispense.resourceType, it) } }

    activityFlow = null
    handler = null
    proposal = null
    plan = null
    order = null
    event = null
    _phase.value = if (_initialized.value) FlowPhase.PROPOSAL else FlowPhase.INITIALIZE
  }

  private suspend fun createProposal() {
    val generated = proposalHandler.generateProposal(configuration)
      ?: error("\$apply generated no proposal: the plan's applicability condition rejected the patient.")

    activityFlow = ActivityFlow.of(repository, generated)
    handler = ActivityHandler(requireNotNull(activityFlow))
    proposal = generated
    plan = null
    order = null
    event = null
    _phase.value = FlowPhase.PLAN
  }

  /** Runs a phase transition, then records the resource it produced and the phase it unlocks. */
  private suspend fun advance(next: FlowPhase, transition: suspend () -> Result<Unit>) {
    transition().getOrThrow()

    when (next) {
      FlowPhase.ORDER -> plan = currentRequestResource()
      FlowPhase.PERFORM -> order = currentRequestResource()
      else -> event = currentEventResource()
    }

    // A transition completes the resource it was based on (initiating the order completes the
    // plan), so refresh the requests we already know about rather than keep stale copies.
    proposal = proposal?.let { refresh(it) }
    plan = plan?.let { refresh(it) }
    order = order?.let { refresh(it) }
    _phase.value = next
  }

  private fun requireHandler() =
    requireNotNull(handler) { "Create the proposal before advancing the flow." }

  private suspend fun <T> withProgress(block: suspend () -> T): T {
    _progress.value = true
    try {
      return block()
    } finally {
      _cards.value = phaseCards()
      _progress.value = false
    }
  }

  private fun phaseCards(): List<PhaseCard> {
    val active = _phase.value
    return listOf(
      PhaseCard(FlowPhase.PROPOSAL, requestDetails(proposal), active == FlowPhase.PROPOSAL),
      PhaseCard(FlowPhase.PLAN, requestDetails(plan), active == FlowPhase.PLAN),
      PhaseCard(FlowPhase.ORDER, requestDetails(order), active == FlowPhase.ORDER),
      PhaseCard(FlowPhase.PERFORM, eventDetails(event), active == FlowPhase.PERFORM),
    )
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
      "ID     : ${request.resourceType}/${request.logicalId}",
      "Intent : ${request.getIntent().code}",
      "Status : ${request.getStatus()}",
      "BasedOn: ${request.getBasedOn()?.reference?.value ?: "—"}",
      "",
      "Additional Info: ${dosage(request.resource.dosageInstruction)}",
    ).joinToString("\n")
  }

  private fun eventDetails(event: CPGMedicationDispenseEvent?): String {
    if (event == null) return "—"
    return listOf(
      "ID     : ${event.resourceType}/${event.logicalId}",
      "Status : ${event.getStatus()}",
      "BasedOn: ${event.getBasedOn()?.reference?.value ?: "—"}",
    ).joinToString("\n")
  }

  private fun dosage(dosageInstruction: List<Dosage>): String =
    dosageInstruction.firstOrNull()?.let { detailsJson.encodeToString(Dosage.serializer(), it) } ?: "—"
}

private val detailsJson = Json {
  prettyPrint = true
  encodeDefaults = false
  explicitNulls = false
}
