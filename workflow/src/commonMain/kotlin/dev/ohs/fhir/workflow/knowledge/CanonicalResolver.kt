package dev.ohs.fhir.workflow.knowledge

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.workflow.repository.WorkflowRepository

/**
 * Resolves knowledge artifacts (PlanDefinition/ActivityDefinition) by canonical URL,
 * ignoring any `|version` suffix. Replaces the cqframework KnowledgeManager.
 */
class CanonicalResolver(private val repository: WorkflowRepository) {

  suspend fun resolveActivityDefinition(canonical: String): ActivityDefinition? =
    resolve("ActivityDefinition", canonical) as? ActivityDefinition

  suspend fun resolvePlanDefinition(canonical: String): PlanDefinition? =
    resolve("PlanDefinition", canonical) as? PlanDefinition

  private suspend fun resolve(type: String, canonical: String) =
    repository.searchByUri(type, "url", canonical.substringBefore("|")).firstOrNull()
}
