package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.FhirEngine
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.fhir.search.ReferenceClientParam
import dev.ohs.fhir.search.Search
import dev.ohs.fhir.search.UriClientParam
import dev.ohs.fhir.workflow.repository.WorkflowRepository

/** Adapts the workflow library's [WorkflowRepository] onto `dev.ohs.fhir:fhir-engine`. */
class EngineWorkflowRepository(private val engine: FhirEngine) : WorkflowRepository {
  override suspend fun read(type: String, id: String): Resource? =
    runCatching { engine.get(ResourceType.fromCode(type), id) }.getOrNull()

  override suspend fun create(resource: Resource): String = engine.create(resource).first()

  override suspend fun update(resource: Resource) = engine.update(resource)

  override suspend fun delete(type: String, id: String) = engine.delete(ResourceType.fromCode(type), id)

  override suspend fun searchByReferenceParam(
    type: String,
    param: String,
    referenceValue: String,
  ): List<Resource> {
    val search =
      Search(ResourceType.fromCode(type)).apply {
        filter(ReferenceClientParam(param), { value = referenceValue })
      }
    return engine.search<Resource>(search).map { it.resource }
  }

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> {
    val search =
      Search(ResourceType.fromCode(type)).apply { filter(UriClientParam(param), { value = uri }) }
    return engine.search<Resource>(search).map { it.resource }
  }
}
