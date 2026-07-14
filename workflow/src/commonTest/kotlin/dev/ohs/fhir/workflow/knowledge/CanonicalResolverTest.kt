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
package dev.ohs.fhir.workflow.knowledge

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CanonicalResolverTest {
  @Test
  fun shouldResolveActivityDefinitionWhenUrlHasVersionSuffix() = runTest {
    val url = "http://example.org/ActivityDefinition/ad-1"
    val repo =
      InMemoryWorkflowRepository().apply {
        registerUriIndex("ActivityDefinition", "url") {
          listOf((it as ActivityDefinition).url?.value ?: "")
        }
        create(
          ActivityDefinition(
            id = "ad-1",
            url = Uri(value = url),
            status = Enumeration(value = PublicationStatus.Active),
          )
        )
      }
    val resolved = CanonicalResolver(repo).resolveActivityDefinition("$url|1.0.0")
    assertEquals("ad-1", resolved?.id)
  }
}
