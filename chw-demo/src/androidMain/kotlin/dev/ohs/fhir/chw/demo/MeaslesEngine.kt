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
package dev.ohs.fhir.chw.demo

import android.content.res.AssetManager
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.cqframework.cql.cql2elm.CqlCompilerOptions
import org.cqframework.cql.cql2elm.CqlTranslator
import org.cqframework.cql.cql2elm.LibraryContentType
import org.cqframework.cql.cql2elm.LibraryManager
import org.cqframework.cql.cql2elm.LibrarySourceProvider
import org.cqframework.cql.cql2elm.ModelManager
import org.hl7.cql.model.ModelIdentifier
import org.hl7.cql.model.ModelInfoProvider
import org.hl7.elm.r1.VersionedIdentifier
import org.hl7.elm_modelinfo.r1.serializing.parseModelInfoXml
import org.opencds.cqf.cql.engine.data.CompositeDataProvider
import org.opencds.cqf.cql.engine.data.DataProvider
import org.opencds.cqf.cql.engine.execution.CqlEngine
import org.opencds.cqf.cql.engine.execution.Environment
import org.opencds.cqf.cql.engine.fhir.fhirModelNamespaceUri
import org.opencds.cqf.cql.engine.fhir.model.SimpleFhirModelResolver
import org.opencds.cqf.cql.engine.fhir.parser.fhirResourceJsonToCqlValue
import org.opencds.cqf.cql.engine.fhir.retrieve.SimpleFhirRetrieveProvider
import org.opencds.cqf.cql.engine.fhir.terminology.SimpleFhirTerminologyProvider
import org.opencds.cqf.cql.engine.runtime.Date as EngineDate

/**
 * WHO smart-immunizations measles logic on the KMP v5 CQL engine, wired from app assets.
 *
 * Compiles the DAK's own `IMMZD2DTMeaslesOngoingTransmissionLogic` (byte-for-byte from the
 * published package's text/cql attachments) once, then evaluates its decision-table
 * expressions per patient. Same plumbing as the Phase 0 spike / cql-v5-probe's ImmzProbe.
 */
class MeaslesEngine private constructor(
  private val libraryManager: LibraryManager,
  private val fhirModel: org.cqframework.cql.cql2elm.model.Model,
  private val terminology: SimpleFhirTerminologyProvider,
  private val rootVersion: String?,
) {

  private fun parseBundle(json: String, model: org.cqframework.cql.cql2elm.model.Model) =
    fhirResourceJsonToCqlValue(Buffer().apply { writeString(json) }, model)

  data class MeaslesStatus(
    val dueMcv1: Boolean,
    val dueMcv2: Boolean,
    val seriesComplete: Boolean,
    val guidance: String?,
    val evalMillis: Long,
  )

  /**
   * Evaluates against the given chart bundle. The compiled library is reused (compilation is
   * the ~6 s cost); the data provider and engine are rebuilt per call so the chart can change
   * between evaluations. Cheap (~70-170 ms), but not thread-safe; serialize access.
   */
  @Synchronized
  fun evaluate(patientId: String, bundleJson: String): MeaslesStatus {
    val t0 = System.currentTimeMillis()
    val dataProvider =
      CompositeDataProvider(
        SimpleFhirModelResolver(fhirModel),
        SimpleFhirRetrieveProvider(parseBundle(bundleJson, fhirModel), terminology),
      )
    val engine =
      CqlEngine(
        Environment(
          libraryManager,
          mutableMapOf<String?, DataProvider?>(fhirModelNamespaceUri to dataProvider),
          terminology,
        ),
        mutableSetOf(CqlEngine.Options.EnableTypeChecking),
      )
    val result =
      engine
        .evaluate {
          library(VersionedIdentifier().withId(ROOT).withVersion(rootVersion)) {
            expressions(DUE_MCV1, DUE_MCV2, SERIES_COMPLETE, GUIDANCE)
          }
          contextParameter = "Patient" to patientId
          parameters = mapOf("Today" to EngineDate(TODAY))
        }
        .onlyResultOrThrow
    fun bool(name: String) =
      (result[name]?.value as? org.opencds.cqf.cql.engine.runtime.Boolean)?.value == true
    return MeaslesStatus(
      dueMcv1 = bool(DUE_MCV1),
      dueMcv2 = bool(DUE_MCV2),
      seriesComplete = bool(SERIES_COMPLETE),
      guidance =
        (result[GUIDANCE]?.value as? org.opencds.cqf.cql.engine.runtime.String)
          ?.value
          ?.takeIf { it.isNotBlank() },
      evalMillis = System.currentTimeMillis() - t0,
    )
  }

  companion object {
    const val ROOT = "IMMZD2DTMeaslesOngoingTransmissionLogic"
    const val DUE_MCV1 = "Client is due for MCV1"
    const val DUE_MCV2 = "Client is due for MCV2"
    const val SERIES_COMPLETE = "Measles primary series is complete"
    const val GUIDANCE = "Guidance"

    // Fixed evaluation date so the demo is clock-stable; sample charts are aged against it.
    const val TODAY = "2026-08-18"

    /** Compiles the WHO CQL and loads terminology. Slow (~6 s on-device); call off-main. */
    fun create(assets: AssetManager): MeaslesEngine {
      val sources =
        assets.list("cql")!!.associate { name ->
          name.removeSuffix(".cql") to assets.open("cql/$name").bufferedReader().readText()
        }

      val modelInfoXml = assets.open("fhir-modelinfo-4.0.1.xml").bufferedReader().readText()
      val modelManager =
        ModelManager().apply {
          modelInfoLoader.registerModelInfoProvider(
            object : ModelInfoProvider {
              override fun load(modelIdentifier: ModelIdentifier) =
                if (modelIdentifier.id == "FHIR") {
                  parseModelInfoXml(Buffer().apply { writeString(modelInfoXml) })
                } else {
                  null
                }
            }
          )
        }
      val options =
        CqlCompilerOptions().apply {
          setOptions(
            CqlCompilerOptions.Options.EnableResultTypes,
            CqlCompilerOptions.Options.EnableLocators,
          )
        }
      val libraryManager =
        LibraryManager(modelManager, options).apply {
          librarySourceLoader.registerProvider(
            object : LibrarySourceProvider {
              override fun getLibrarySource(libraryIdentifier: VersionedIdentifier) =
                sources[libraryIdentifier.id]?.let { Buffer().apply { writeString(it) } }

              override fun getLibraryContent(
                libraryIdentifier: VersionedIdentifier,
                type: LibraryContentType,
              ) = if (type == LibraryContentType.CQL) getLibrarySource(libraryIdentifier) else null
            }
          )
        }

      val translator = CqlTranslator.fromText(sources.getValue(ROOT), libraryManager)
      check(translator.errors.isEmpty()) {
        "WHO CQL failed to compile: ${translator.errors.take(3).map { it.message }}"
      }

      val fhirModel = modelManager.resolveModel("FHIR", "4.0.1")
      fun parseBundle(json: String) =
        fhirResourceJsonToCqlValue(Buffer().apply { writeString(json) }, fhirModel)

      val terminology =
        SimpleFhirTerminologyProvider(
          parseBundle(assets.open("valuesets-bundle.json").bufferedReader().readText())
        )
      val rootVersion =
        Regex("^\\s*library\\s+\"?[\\w.]+\"?\\s+version\\s+'([^']+)'", RegexOption.MULTILINE)
          .find(sources.getValue(ROOT))
          ?.groupValues
          ?.get(1)
      return MeaslesEngine(libraryManager, fhirModel, terminology, rootVersion)
    }
  }
}
