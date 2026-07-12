package dev.ohs.fhir.workflow.activity

import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.event.PerformPhase
import dev.ohs.fhir.workflow.activity.phase.request.OrderPhase
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGCommunicationEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGServiceReportEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGTaskEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
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
  }
}
