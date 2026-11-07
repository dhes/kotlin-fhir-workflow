package dev.ohs.fhir.workflow.cql.probe

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ValueSet
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.cql.CqlExpressionEvaluator
import dev.ohs.fhir.workflow.cql.FhirCqlEvaluator
import dev.ohs.fhir.workflow.cql.InMemoryTerminologyProvider
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import java.io.File
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opencds.cqf.cql.engine.runtime.Date

/** Probe only: the timing steps of CqlTimingProbeTest, then WHO's whole suite with outcome counts. */
class Probe(private val features: File, private val packageDir: File, private val log: (String) -> Unit) {
  private val json = Json {
    encodeDefaults = false
    explicitNulls = false
  }

  private fun <T> timed(step: String, block: () -> T): T {
    val mark = TimeSource.Monotonic.markNow()
    return block().also { log("TIMING | $step | ${mark.elapsedNow().inWholeMilliseconds} ms") }
  }

  private fun packageJson(prefix: String) =
    packageDir
      .listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".json") }!!
      .sortedBy { it.name }
      .map { json.parseToJsonElement(it.readText()).jsonObject }

  private fun decode(o: JsonObject) = json.decodeFromJsonElement(Resource.serializer(), o)

  private fun resource(path: String): String =
    javaClass.classLoader!!.getResource(path)?.readText() ?: error("$path not on the classpath")

  fun run() = runBlocking {
    val start = TimeSource.Monotonic.markNow()
    val modelInfo = timed("read model info XML") { resource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml") }
    val fhirHelpers = resource("org/hl7/fhir/FHIRHelpers-4.0.1.cql")
    val definitions = timed("decode PlanDefinitions and ActivityDefinitions") {
      (packageJson("PlanDefinition-") + packageJson("ActivityDefinition-")).associate {
        it.getValue("url").jsonPrimitive.content to decode(it)
      }
    }
    val libraries = timed("read Library resources") { packageJson("Library-") }
    val libraryNames =
      libraries.associate {
        it.getValue("url").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content
      }
    val cqlSources =
      libraries
        .mapNotNull { library ->
          val cql =
            library["content"]?.jsonArray?.map { it.jsonObject }?.firstOrNull {
              it["contentType"]?.jsonPrimitive?.content == "text/cql"
            } ?: return@mapNotNull null
          library.getValue("name").jsonPrimitive.content to
            Base64.getDecoder().decode(cql.getValue("data").jsonPrimitive.content).decodeToString()
        }
        .toMap() + ("FHIRHelpers" to fhirHelpers)
    val valueSets = timed("decode ValueSets") { packageJson("ValueSet-").map { decode(it) as ValueSet } }
    val evaluator = timed("create FhirCqlEvaluator (parses the model info)") {
      FhirCqlEvaluator({ name: String -> cqlSources[name] }, modelInfo, InMemoryTerminologyProvider(valueSets))
    }
    val resolver = CanonicalResolver { _, canonical -> definitions[canonical.substringBefore("|")] }
    log("TIMING | start-up before the first \$apply | ${start.elapsedNow().inWholeMilliseconds} ms")

    val testCases = WhoImmunizationsTestCases.read(features)
    val byPlan = testCases.groupBy { it.planDefinition }
    val outcomes = mutableMapOf<String, Int>()
    val firstCalls = mutableListOf<Duration>()
    val laterCalls = mutableMapOf<String, MutableList<Duration>>()
    val suite = TimeSource.Monotonic.markNow()
    for ((planName, cases) in byPlan) {
      val plan = definitions["http://smart.who.int/immunizations/PlanDefinition/$planName"] as PlanDefinition
      cases.forEachIndexed { i, testCase ->
        val resources =
          runCatching {
              (json.decodeFromString(Resource.serializer(), testCase.bundle.readText()) as Bundle)
                .entry
                .mapNotNull { it.resource }
            }
            .getOrNull()
        if (resources == null) {
          outcomes.merge("bundle not decoded", 1, Int::plus)
          return@forEachIndexed
        }
        val patient = resources.filterIsInstance<Patient>().single { it.id == testCase.patient }
        val today = testCase.today
        val cql =
          CqlExpressionEvaluator(
            evaluator,
            { _, _ -> resources.filter { it !== patient } },
            mapOf("Today" to Date(today.year, today.monthNumber, today.dayOfMonth)),
            libraryNames::get,
          )
        val operator = FhirOperator(NoRepository, ExpressionEvaluatorRouter(elm = cql), resolver)
        val mark = TimeSource.Monotonic.markNow()
        val carePlan = runCatching { operator.generateCarePlan(plan, patient, today = today) }
        val elapsed = mark.elapsedNow()
        if (i == 0) firstCalls += elapsed else laterCalls.getOrPut(planName) { mutableListOf() } += elapsed
        val outcome =
          carePlan.fold(
            { cp ->
              val guidance =
                cp.contained.filterIsInstance<CommunicationRequest>().firstNotNullOfOrNull {
                  it.payload.firstOrNull()?.content?.asString()?.value?.value
                }
              when {
                testCase.expectedContained != null && testCase.expectedContained != cp.contained.size ->
                  "contained count differs"
                testCase.expectedGuidance != null && testCase.expectedGuidance != guidance -> "guidance differs"
                else -> "pass"
              }
            },
            { "failed: ${it::class.simpleName}" },
          )
        outcomes.merge(outcome, 1, Int::plus)
      }
    }
    log("SUITE | ${testCases.size} test cases, ${byPlan.size} PlanDefinitions in ${suite.elapsedNow().inWholeMilliseconds} ms | $outcomes")
    val first = firstCalls.sorted()
    log("SUITE | first \$apply per PlanDefinition (compiles its libraries) | median ${first[first.size / 2].inWholeMilliseconds} ms, max ${first.last().inWholeMilliseconds} ms")
    val later = laterCalls.values.flatten().sorted()
    log("SUITE | later \$apply | median ${later[later.size / 2].inWholeMilliseconds} ms, 90th percentile ${later[later.size * 9 / 10].inWholeMilliseconds} ms, max ${later.last().inWholeMilliseconds} ms")
    for (name in listOf("IMMZD18SPolioBOPVPlusIPV", "IMMZD2DTPolioBOPVPlusIPV", "IMMZD18SBCG")) {
      val t = laterCalls[name].orEmpty().sorted()
      if (t.isNotEmpty()) log("SUITE | $name later \$apply | median ${t[t.size / 2].inWholeMilliseconds} ms, max ${t.last().inWholeMilliseconds} ms")
    }
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
