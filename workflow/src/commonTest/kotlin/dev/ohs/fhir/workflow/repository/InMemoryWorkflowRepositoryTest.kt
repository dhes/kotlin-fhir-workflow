package dev.ohs.fhir.workflow.repository

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryWorkflowRepositoryTest {
  @Test
  fun `create then read returns the resource`() = runTest {
    val repo = InMemoryWorkflowRepository()
    repo.create(CommunicationRequest(id = "cr-1", status = statusActive()))
    val read = repo.read("CommunicationRequest", "cr-1")
    assertEquals("cr-1", read?.id)
  }

  @Test
  fun `read missing returns null`() = runTest {
    assertNull(InMemoryWorkflowRepository().read("CommunicationRequest", "nope"))
  }

  private fun statusActive() =
    dev.ohs.fhir.model.r4.Enumeration(
      value = CommunicationRequest.RequestStatus.Active,
    )
}
