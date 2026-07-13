package dev.ohs.fhir.workflow.testing

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.logicalId
import dev.ohs.fhir.workflow.repository.WorkflowRepository
import dev.ohs.fhir.workflow.resourceTypeName

/**
 * In-memory [WorkflowRepository] test double. It cannot generically read search-param
 * values off arbitrary resources without the engine's indexer, so it accepts pluggable
 * extractors keyed by "type|param"; tests register only what they use.
 */
class InMemoryWorkflowRepository : WorkflowRepository {
  // key = "Type/id"
  private val store = linkedMapOf<String, Resource>()

  // key = "Type|param" -> function returning the indexed value(s) for a resource
  private val referenceIndex = mutableMapOf<String, (Resource) -> List<String>>()
  private val uriIndex = mutableMapOf<String, (Resource) -> List<String>>()

  fun registerReferenceIndex(type: String, param: String, extractor: (Resource) -> List<String>) {
    referenceIndex["$type|$param"] = extractor
  }

  fun registerUriIndex(type: String, param: String, extractor: (Resource) -> List<String>) {
    uriIndex["$type|$param"] = extractor
  }

  override suspend fun read(type: String, id: String): Resource? = store["$type/$id"]

  override suspend fun create(resource: Resource): String {
    val id = resource.logicalId ?: error("resource has no id")
    store["${resource.resourceTypeName()}/$id"] = resource
    return id
  }

  override suspend fun update(resource: Resource) {
    val id = resource.logicalId ?: error("resource has no id")
    store["${resource.resourceTypeName()}/$id"] = resource
  }

  override suspend fun delete(type: String, id: String) {
    store.remove("$type/$id")
  }

  override suspend fun searchByReferenceParam(type: String, param: String, referenceValue: String): List<Resource> {
    val extractor = referenceIndex["$type|$param"] ?: return emptyList()
    return store.values.filter { it.resourceTypeName() == type && extractor(it).contains(referenceValue) }
  }

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> {
    val extractor = uriIndex["$type|$param"] ?: return emptyList()
    return store.values.filter { it.resourceTypeName() == type && extractor(it).contains(uri) }
  }
}
