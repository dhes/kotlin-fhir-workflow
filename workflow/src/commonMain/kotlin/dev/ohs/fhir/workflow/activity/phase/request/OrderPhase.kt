package dev.ohs.fhir.workflow.activity.phase.request

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.checkReferencesEqual
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.repository.WorkflowRepository
import kotlin.uuid.Uuid

@Suppress("UNCHECKED_CAST")
class OrderPhase<R : CPGRequestResource<*>>(repository: WorkflowRepository, r: R) :
  BaseRequestPhase<R>(repository, r, Phase.PhaseName.ORDER) {

  companion object {
    private val AllowedIntents = listOf(Intent.PROPOSAL, Intent.PLAN)
    private val AllowedPhases = listOf(Phase.PhaseName.PROPOSAL, Phase.PhaseName.PLAN)

    fun <R : CPGRequestResource<*>> prepare(inputPhase: Phase): Result<R> = runCatching {
      check(inputPhase.getPhaseName() in AllowedPhases) { "An Order can't be created for a flow in ${inputPhase.getPhaseName().name} phase." }
      val inputRequest = (inputPhase as BaseRequestPhase<*>).request
      check(inputRequest.getIntent() in AllowedIntents) { "Order can't be created for a request with ${inputRequest.getIntent()} intent." }
      check(inputRequest.getStatus() == Status.ACTIVE) { "${inputPhase.getPhaseName().name} request is still in ${inputRequest.getStatusCode()} status." }
      inputRequest.copy(id = Uuid.random().toString(), status = Status.DRAFT, intent = Intent.ORDER) as R
    }

    suspend fun <R : CPGRequestResource<*>> initiate(
      repository: WorkflowRepository,
      inputPhase: Phase,
      inputOrder: R,
    ): Result<OrderPhase<R>> = runCatching {
      check(inputPhase.getPhaseName() in AllowedPhases) { "An Order can't be started for a flow in ${inputPhase.getPhaseName().name} phase." }
      val currentPhase = inputPhase as BaseRequestPhase<*>
      val basedOn = inputOrder.getBasedOn()
      require(basedOn != null) { "${inputOrder.resource::class.simpleName}.basedOn can't be null." }
      require(checkReferencesEqual(basedOn, currentPhase.request.asReference())) { "Provided draft is not based on the request in current phase." }
      val basedOnResource: Resource? = repository.read(inputOrder.resourceType, currentPhase.request.logicalId!!)
      val basedOnRequest = basedOnResource?.let { CPGRequestResource.of(it) }
      require(basedOnRequest != null) { "Couldn't find ${basedOn.reference?.value} in the database." }
      require(basedOnRequest.getIntent() in AllowedIntents) { "Order can't be based on a request with ${basedOnRequest.getIntent()} intent." }
      require(basedOnRequest.getStatus() == Status.ACTIVE) { "Order can't be based on a request with ${basedOnRequest.getStatusCode()} status." }
      require(inputOrder.getIntent() == Intent.ORDER) { "Input request has '${inputOrder.getIntent()}' intent." }
      require(inputOrder.getStatus() in AllowedStatusForPhaseStart) { "Input request is in ${inputOrder.getStatusCode()} status." }
      basedOnRequest.setStatus(Status.COMPLETED)
      repository.create(inputOrder.resource)
      repository.update(basedOnRequest.resource)
      OrderPhase(repository, inputOrder)
    }
  }
}
