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
package dev.ohs.fhir.workflow.cql

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.WorkflowRepository

/** A repository that holds nothing, for tests that resolve every canonical elsewhere. */
internal object EmptyWorkflowRepository : WorkflowRepository {
  override suspend fun read(type: String, id: String): Resource? = null

  override suspend fun create(resource: Resource): String = error("not used")

  override suspend fun update(resource: Resource) = error("not used")

  override suspend fun delete(type: String, id: String) = error("not used")

  override suspend fun searchByReferenceParam(
    type: String,
    param: String,
    referenceValue: String,
  ): List<Resource> = emptyList()

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
    emptyList()
}
