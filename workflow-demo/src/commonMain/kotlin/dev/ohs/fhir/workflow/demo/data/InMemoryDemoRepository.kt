package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.repository.WorkflowRepository

/**
 * Pure-KMP [WorkflowRepository] backed by an in-memory map, for platforms without
 * `dev.ohs.fhir:fhir-engine` (e.g. wasmJs). Search is not needed by the demo's ActivityFlow
 * lifecycle, so it's a no-op.
 */
class InMemoryDemoRepository : WorkflowRepository {
  private val resources = mutableMapOf<String, Resource>()

  override suspend fun read(type: String, id: String): Resource? = resources["$type/$id"]

  override suspend fun create(resource: Resource): String {
    val type = resource::class.simpleName ?: error("Unable to determine resource type")
    val id = requireNotNull(resource.id) { "Resource must have an id to be created" }
    resources["$type/$id"] = resource
    return id
  }

  override suspend fun update(resource: Resource) {
    val type = resource::class.simpleName ?: error("Unable to determine resource type")
    val id = requireNotNull(resource.id) { "Resource must have an id to be updated" }
    resources["$type/$id"] = resource
  }

  override suspend fun searchByReferenceParam(
    type: String,
    param: String,
    referenceValue: String,
  ): List<Resource> = emptyList()

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
    emptyList()
}
