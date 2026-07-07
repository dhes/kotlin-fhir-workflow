package dev.ohs.fhir.workflow.activity.phase

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PhaseTransitionsTest {
  private fun proposal(repo: InMemoryWorkflowRepository): ProposalPhase<CPGCommunicationRequest> {
    val cr = CPGCommunicationRequest(
      CommunicationRequest(id = "cr-1", status = Enumeration(value = CommunicationRequest.RequestStatus.Active)),
    ).apply { setIntent(Intent.PROPOSAL) }
    return ProposalPhase(repo, cr).also { runCatchingCreate(repo, cr.resource) }
  }
  private fun runCatchingCreate(repo: InMemoryWorkflowRepository, r: dev.ohs.fhir.model.r4.Resource) {
    kotlinx.coroutines.runBlocking { repo.create(r) }
  }

  @Test
  fun `prepare plan from proposal succeeds`() = runTest {
    val repo = InMemoryWorkflowRepository()
    val prepared = PlanPhase.prepare<CPGCommunicationRequest>(proposal(repo))
    assertTrue(prepared.isSuccess)
  }
}
