package dev.ohs.fhir.workflow.cql.probe

import android.app.Activity
import android.os.Bundle
import android.util.Log
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Probe only: the same app without CQL, applying a FHIRPath PlanDefinition, for the size baseline. */
class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val json = Json { ignoreUnknownKeys = true }
    val plan =
      json.decodeFromString(
        Resource.serializer(),
        """{"resourceType":"PlanDefinition","status":"active","action":[{"condition":[{"kind":"applicability",""" +
          """"expression":{"language":"text/fhirpath","expression":"true"}}]}]}""",
      ) as PlanDefinition
    val patient = json.decodeFromString(Resource.serializer(), """{"resourceType":"Patient","id":"p1"}""")
    val carePlan = runBlocking {
      FhirOperator(NoRepository).generateCarePlan(plan, patient, today = LocalDate(2026, 11, 6))
    }
    Log.i("CQLPROBE", "FHIRPath only: ${carePlan.contained.size} contained")
  }

  private object NoRepository : WorkflowRepository {
    override suspend fun read(type: String, id: String): Resource? = null
    override suspend fun create(resource: Resource): String = error("not used")
    override suspend fun update(resource: Resource) = error("not used")
    override suspend fun delete(type: String, id: String) = error("not used")
    override suspend fun searchByReferenceParam(type: String, param: String, referenceValue: String) =
      emptyList<Resource>()
    override suspend fun searchByUri(type: String, param: String, uri: String) = emptyList<Resource>()
  }
}
