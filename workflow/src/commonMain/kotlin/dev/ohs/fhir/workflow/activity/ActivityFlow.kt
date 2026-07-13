package dev.ohs.fhir.workflow.activity

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.ReadOnlyRequestPhase
import dev.ohs.fhir.workflow.activity.phase.event.PerformPhase
import dev.ohs.fhir.workflow.activity.phase.request.OrderPhase
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGCommunicationEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGServiceReportEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGTaskEvent
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.ref
import dev.ohs.fhir.workflow.repository.WorkflowRepository

class ActivityFlow<R : CPGRequestResource<*>, E : CPGEventResource<*>> private constructor(
  private val repository: WorkflowRepository,
  requestResource: R? = null,
  eventResource: E? = null,
) {
  private var currentPhase: Phase =
    when {
      eventResource != null -> PerformPhase(repository, eventResource)
      requestResource != null -> when (requestResource.getIntent()) {
        Intent.PROPOSAL -> ProposalPhase(repository, requestResource)
        Intent.PLAN -> PlanPhase(repository, requestResource)
        Intent.ORDER -> OrderPhase(repository, requestResource)
        else -> throw IllegalArgumentException(
          "Couldn't create the flow for ${requestResource.getIntent()} intent. Supported: proposal, plan, order.",
        )
      }
      else -> throw IllegalArgumentException("Either Request or Event is required to create a flow.")
    }

  fun getCurrentPhase(): Phase = currentPhase

  /** Returns a read-only list of all the previous phases of the flow, walking the `basedOn` chain. */
  @Suppress("UNCHECKED_CAST")
  suspend fun getPreviousPhases(): List<ReadOnlyRequestPhase<R>> {
    val phases = mutableListOf<ReadOnlyRequestPhase<R>>()
    var current: Phase? = currentPhase
    while (current != null) {
      val c = current
      val basedOn: Reference? = when (c) {
        is Phase.RequestPhase<*> -> c.getRequestResource().getBasedOn()
        is Phase.EventPhase<*> -> c.getEventResource().getBasedOn()
        else -> null
      }
      val basedOnRequest: R? =
        basedOn?.ref?.let { ref ->
          repository.read(ref.substringBefore("/"), ref.substringAfter("/"))?.let {
            CPGRequestResource.of(it) as R
          }
        }
      current = when (basedOnRequest?.getIntent()) {
        Intent.PROPOSAL -> ProposalPhase(repository, basedOnRequest)
        Intent.PLAN -> PlanPhase(repository, basedOnRequest)
        Intent.ORDER -> OrderPhase(repository, basedOnRequest)
        else -> null
      }
      current?.let { phases.add(it as ReadOnlyRequestPhase<R>) }
    }
    return phases
  }

  suspend fun preparePlan(): Result<R> = PlanPhase.prepare(currentPhase)
  suspend fun initiatePlan(preparedPlan: R): Result<PlanPhase<R>> =
    PlanPhase.initiate(repository, currentPhase, preparedPlan).onSuccess { currentPhase = it }
  suspend fun prepareOrder(): Result<R> = OrderPhase.prepare(currentPhase)
  suspend fun initiateOrder(preparedOrder: R): Result<OrderPhase<R>> =
    OrderPhase.initiate(repository, currentPhase, preparedOrder).onSuccess { currentPhase = it }
  suspend fun <D : E> preparePerform(eventClassName: String): Result<D> =
    PerformPhase.prepare(eventClassName, currentPhase)
  suspend fun <D : E> initiatePerform(preparedEvent: D): Result<PerformPhase<D>> =
    PerformPhase.initiate(repository, currentPhase, preparedEvent).onSuccess { currentPhase = it }

  companion object {
    fun of(repository: WorkflowRepository, resource: CPGCommunicationRequest):
      ActivityFlow<CPGCommunicationRequest, CPGCommunicationEvent> = ActivityFlow(repository, resource)

    fun of(repository: WorkflowRepository, resource: CPGCommunicationEvent):
      ActivityFlow<CPGCommunicationRequest, CPGCommunicationEvent> = ActivityFlow(repository, null, resource)

    fun of(repository: WorkflowRepository, resource: CPGMedicationRequest):
      ActivityFlow<CPGMedicationRequest, CPGEventResource<*>> = ActivityFlow(repository, resource)

    fun of(repository: WorkflowRepository, resource: CPGTaskRequest):
      ActivityFlow<CPGTaskRequest, CPGTaskEvent> = ActivityFlow(repository, resource)

    fun of(repository: WorkflowRepository, resource: CPGServiceRequest):
      ActivityFlow<CPGServiceRequest, CPGServiceReportEvent> = ActivityFlow(repository, resource)

    /**
     * Returns the active (non-completed) flows for the [patientId], reconstructed from persistence.
     * Events and requests are searched by subject, chained via `basedOn`, and any flow whose latest
     * request/event is completed is dropped. NOTE: when a new activity type is added, register its
     * event/request resource types below so this search can find it.
     */
    suspend fun of(
      repository: WorkflowRepository,
      patientId: String,
    ): List<ActivityFlow<CPGRequestResource<*>, CPGEventResource<*>>> {
      val subject = "Patient/$patientId"

      val events =
        listOf("MedicationDispense", "Communication")
          .flatMap { repository.searchByReferenceParam(it, "subject", subject) }
          .map { CPGEventResource.of(it) }

      val idToRequestMap: MutableMap<String, CPGRequestResource<*>> =
        listOf("MedicationRequest", "CommunicationRequest")
          .flatMap { repository.searchByReferenceParam(it, "subject", subject) }
          .map { CPGRequestResource.of(it) }
          .associateByTo(LinkedHashMap()) { "${it.resourceType}/${it.logicalId}" }

      fun addBasedOn(chain: RequestChain): RequestChain? {
        val basedOn = chain.request?.getBasedOn() ?: chain.event?.getBasedOn()
        return basedOn?.ref?.let { ref ->
          idToRequestMap[ref]?.let { requestResource ->
            idToRequestMap.remove(ref)
            RequestChain(request = requestResource).apply { this.basedOn = addBasedOn(this) }
          }
        }
      }

      val requestChain =
        events.map { RequestChain(event = it).apply { this.basedOn = addBasedOn(this) } } +
          idToRequestMap.values
            .filter {
              it.getIntent() == Intent.PROPOSAL ||
                it.getIntent() == Intent.PLAN ||
                it.getIntent() == Intent.ORDER
            }
            .sortedByDescending { it.getIntent().code ?: "" }
            .mapNotNull {
              if (idToRequestMap.containsKey("${it.resourceType}/${it.logicalId}")) {
                RequestChain(request = it).apply { this.basedOn = addBasedOn(this) }
              } else {
                null
              }
            }

      return requestChain
        .filter {
          when {
            it.event != null -> it.event.getStatus() != EventStatus.COMPLETED
            it.request != null -> it.request.getStatus() != Status.COMPLETED
            else -> false
          }
        }
        .map { ActivityFlow(repository, it.request, it.event) }
    }
  }
}

/**
 * The chain of event/requests of an activity flow. A [RequestChain] holds either a [request] or an
 * [event], plus the parent it is [basedOn].
 */
private data class RequestChain(
  val request: CPGRequestResource<*>? = null,
  val event: CPGEventResource<*>? = null,
  var basedOn: RequestChain? = null,
)
