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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A repository over a list, whose reference search matches a top-level element of the search
 * parameter's name.
 */
internal class ListWorkflowRepository : WorkflowRepository {
  private val resources = mutableListOf<Resource>()

  override suspend fun read(type: String, id: String): Resource? =
    resources.firstOrNull { type(it) == type && it.id == id }

  override suspend fun create(resource: Resource): String {
    resources += resource
    return resource.id ?: error("resource has no id")
  }

  override suspend fun update(resource: Resource) = error("not used")

  override suspend fun delete(type: String, id: String) = error("not used")

  override suspend fun searchByReferenceParam(
    type: String,
    param: String,
    referenceValue: String,
  ): List<Resource> =
    resources.filter {
      type(it) == type &&
        (json(it)[param] as? JsonObject)?.get("reference")?.jsonPrimitive?.content == referenceValue
    }

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
    emptyList()

  private fun json(resource: Resource): JsonObject =
    Json.encodeToJsonElement(Resource.serializer(), resource).jsonObject

  private fun type(resource: Resource): String =
    json(resource)["resourceType"]!!.jsonPrimitive.content
}
