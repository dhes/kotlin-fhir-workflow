package dev.ohs.fhir.workflow.activity.phase

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.workflow.activity.phase.request.BaseRequestPhase
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BaseRequestPhaseTest {
  private class TestPhase(repo: dev.ohs.fhir.workflow.repository.WorkflowRepository, r: CPGRequestResource<*>) :
    BaseRequestPhase<CPGRequestResource<*>>(repo, r, Phase.PhaseName.PROPOSAL)

  @Test
  fun `suspend transitions active to on-hold and persists`() = runTest {
    val repo = InMemoryWorkflowRepository()
    val cr = CPGCommunicationRequest(
      CommunicationRequest(id = "cr-1", status = Enumeration(value = CommunicationRequest.RequestStatus.Active)),
    ).apply { setIntent(Intent.PROPOSAL) }
    repo.create(cr.resource)
    val phase = TestPhase(repo, cr)
    val result = phase.suspendPhase("busy")
    assertTrue(result.isSuccess)
    assertTrue(phase.getRequestResource().getStatus() == Status.ONHOLD)
  }
}
