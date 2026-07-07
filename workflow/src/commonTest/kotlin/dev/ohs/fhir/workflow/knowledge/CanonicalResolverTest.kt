package dev.ohs.fhir.workflow.knowledge

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalResolverTest {
  @Test
  fun `resolves activity definition by url ignoring version suffix`() = runTest {
    val url = "http://example.org/ActivityDefinition/ad-1"
    val repo = InMemoryWorkflowRepository().apply {
      registerUriIndex("ActivityDefinition", "url") { listOf((it as ActivityDefinition).url?.value ?: "") }
      create(ActivityDefinition(id = "ad-1", url = Uri(value = url), status = Enumeration(value = PublicationStatus.Active)))
    }
    val resolved = CanonicalResolver(repo).resolveActivityDefinition("$url|1.0.0")
    assertEquals("ad-1", resolved?.id)
  }
}
