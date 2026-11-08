package dev.ohs.fhir.workflow.cql

import kotlin.test.Test
import kotlin.time.measureTimedValue
import org.cqframework.cql.cql2elm.ModelManager
import org.cqframework.cql.cql2elm.createModelInfoProvider
import org.cqframework.cql.shared.serializing.parseXml
import org.hl7.cql.model.ModelIdentifier
import org.hl7.cql.model.ModelInfoProvider
import org.hl7.cql.model.NamespaceManager
import org.hl7.cql.model.SystemModelInfoProvider
import org.hl7.elm_modelinfo.r1.serializing.parseModelInfoXml
import kotlinx.io.Buffer
import kotlinx.io.writeString

/** Probe only: times the parts of resolving the FHIR model. */
class ModelInfoTimingProbeTest {
  private val xml =
    System.getenv("FHIR_MODELINFO")?.let { java.io.File(it).readText() }
      ?: checkNotNull(javaClass.classLoader.getResource("org/hl7/fhir/fhir-modelinfo-4.0.1.xml")).readText()

  private fun manager(provider: ModelInfoProvider) =
    ModelManager(NamespaceManager(), false).apply {
      modelInfoLoader.registerModelInfoProvider(SystemModelInfoProvider())
      modelInfoLoader.registerModelInfoProvider(provider)
    }

  @Test
  fun constructorParts() {
    val (model, tModel) =
      measureTimedValue {
        manager(
            createModelInfoProvider { id, _, _ ->
              if (id == "FHIR") Buffer().apply { writeString(xml) } else null
            }
          )
          .resolveModel("FHIR", "4.0.1")
      }
    val (_, tResolver) = measureTimedValue { KotlinFhirModelResolver(model) }
    val (_, tResolver2) = measureTimedValue { KotlinFhirModelResolver(model) }
    val (_, tEvaluator) = measureTimedValue { FhirCqlEvaluator(listOf<String>(), xml) }
    val (_, tEvaluator2) = measureTimedValue { FhirCqlEvaluator(listOf<String>(), xml) }
    println(
      "PROBE cold resolveModel=${tModel.inWholeMilliseconds} KotlinFhirModelResolver=${tResolver.inWholeMilliseconds} " +
        "again=${tResolver2.inWholeMilliseconds} FhirCqlEvaluator=${tEvaluator.inWholeMilliseconds} again=${tEvaluator2.inWholeMilliseconds}"
    )
  }

  @Test
  fun timing() {
    repeat(6) { run ->
      val (tree, tTree) = measureTimedValue { parseXml(xml) }
      val (info, tInfo) = measureTimedValue { parseModelInfoXml(xml) }
      check(tree.toString().isNotEmpty())
      val parsedOnce = info
      val (_, tBuild) =
        measureTimedValue {
          manager(
              object : ModelInfoProvider {
                override fun load(modelIdentifier: ModelIdentifier) =
                  if (modelIdentifier.id == "FHIR") parsedOnce else null
              }
            )
            .resolveModel("FHIR", "4.0.1")
        }
      val (_, tFull) =
        measureTimedValue {
          manager(
              createModelInfoProvider { id, _, _ ->
                if (id == "FHIR") Buffer().apply { writeString(xml) } else null
              }
            )
            .resolveModel("FHIR", "4.0.1")
        }
      println(
        "PROBE run=$run xmlTree=${tTree.inWholeMilliseconds} parseModelInfoXml=${tInfo.inWholeMilliseconds} " +
          "buildModelFromParsed=${tBuild.inWholeMilliseconds} fullResolve=${tFull.inWholeMilliseconds} " +
          "types=${info.typeInfo.size}"
      )
    }
  }
}
