package dev.ohs.fhir.workflow.activity.phase.request

import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.repository.WorkflowRepository

class ProposalPhase<R : CPGRequestResource<*>>(repository: WorkflowRepository, r: R) :
  BaseRequestPhase<R>(repository, r, Phase.PhaseName.PROPOSAL)
