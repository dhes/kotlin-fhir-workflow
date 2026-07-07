package dev.ohs.fhir.workflow.activity

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ActivityFlowTest {
  @Test
  fun `proposal flow can prepare a plan`() = runTest {
    val repo = InMemoryWorkflowRepository()
    val request = CPGCommunicationRequest(
      CommunicationRequest(id = "cr-1", status = Enumeration(value = CommunicationRequest.RequestStatus.Active)),
    ).apply { setIntent(Intent.PROPOSAL) }
    repo.create(request.resource)

    val flow = ActivityFlow.of(repo, request)
    flow.getCurrentPhase().shouldBeInstanceOf<ProposalPhase<*>>()
    assertTrue(flow.preparePlan().isSuccess)
  }
}
