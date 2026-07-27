/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.PlanDefinition

/**
 * Resolves knowledge artifacts (PlanDefinition/ActivityDefinition) by canonical URL, ignoring any
 * `|version` suffix. Replaces the cqframework KnowledgeManager.
 */
class CanonicalResolver(private val repository: WorkflowRepository) {

  suspend fun resolveActivityDefinition(canonical: String): ActivityDefinition? =
    resolve("ActivityDefinition", canonical) as? ActivityDefinition

  suspend fun resolvePlanDefinition(canonical: String): PlanDefinition? =
    resolve("PlanDefinition", canonical) as? PlanDefinition

  private suspend fun resolve(type: String, canonical: String) =
    repository.searchByUri(type, "url", canonical.substringBefore("|")).firstOrNull()
}
