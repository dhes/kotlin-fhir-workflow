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

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
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
 * Phase 0 spike: prove WHO's published smart-immunizations measles CQL — the DAK's own
 * text/cql attachments, byte-for-byte — compiles and evaluates on-device with the KMP v5
 * engine. Same wiring as cql-v5-probe's ImmzProbe, with assets in place of the package dir.
 */
class MainActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val text =
      TextView(this).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setPadding(32, 32, 32, 32)
        setText("Compiling WHO measles CQL on-device…")
      }
    setContentView(ScrollView(this).apply { addView(text) })

    Thread {
        val out = StringBuilder()
        try {
          runSpike { line ->
            Log.i(TAG, line)
            out.appendLine(line)
          }
        } catch (t: Throwable) {
          Log.e(TAG, "spike failed", t)
          out.appendLine("FAILED: $t").appendLine(Log.getStackTraceString(t))
        }
        runOnUiThread { text.text = out.toString() }
      }
      .start()
  }

  private fun runSpike(log: (String) -> Unit) {
    // -- 1. WHO's CQL from assets (measles include-closure + FHIRHelpers) --
    val sources =
      assets.list("cql")!!.associate { name ->
        name.removeSuffix(".cql") to assets.open("cql/$name").bufferedReader().readText()
      }
    log("loaded ${sources.size} CQL libraries from assets")

    // -- 2. Compiler plumbing (ImmzProbe shape) --
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
        setOptions(CqlCompilerOptions.Options.EnableResultTypes, CqlCompilerOptions.Options.EnableLocators)
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

    // -- 3. Compile the measles root (include closure resolves through the assets) --
    var t = System.currentTimeMillis()
    val translator = CqlTranslator.fromText(sources.getValue(MEASLES_ROOT), libraryManager)
    if (translator.errors.isNotEmpty()) {
      log("COMPILE FAILED: ${translator.errors.size} error(s)")
      translator.errors.take(5).forEach { log("  ${it.message?.take(300)}") }
      return
    }
    log("COMPILED $MEASLES_ROOT in ${System.currentTimeMillis() - t} ms")

    // -- 4. Evaluate against a synthetic chart through the #1815 providers --
    val fhirModel = modelManager.resolveModel("FHIR", "4.0.1")
    fun parseBundle(json: String) =
      fhirResourceJsonToCqlValue(Buffer().apply { writeString(json) }, fhirModel)

    t = System.currentTimeMillis()
    val terminology =
      SimpleFhirTerminologyProvider(
        parseBundle(assets.open("valuesets-bundle.json").bufferedReader().readText())
      )
    log("loaded ValueSet bundle in ${System.currentTimeMillis() - t} ms")

    val dataProvider =
      CompositeDataProvider(
        SimpleFhirModelResolver(fhirModel),
        SimpleFhirRetrieveProvider(parseBundle(DATA_BUNDLE), terminology),
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

    t = System.currentTimeMillis()
    val result =
      engine
        .evaluate {
          library(VersionedIdentifier().withId(MEASLES_ROOT)) { expressions(*EXPRESSIONS) }
          contextParameter = "Patient" to "measles-inf-9m"
          parameters = mapOf("Today" to EngineDate(TODAY))
        }
        .onlyResultOrThrow
    log("EVALUATED [measles-inf-9m] boy 9mo, 0 doses in ${System.currentTimeMillis() - t} ms")
    EXPRESSIONS.forEach { log("  $it = ${result[it]?.value}") }
    log("")
    log("WHO's published measles CQL runs on-device. Phase 0 complete.")
  }

  companion object {
    private const val TAG = "ChwSpike"
    private const val MEASLES_ROOT = "IMMZD2DTMeaslesMCVDose0Logic"
    private val EXPRESSIONS = arrayOf("Guidance", "Has Guidance")

    // Fixed evaluation date so results are clock-stable (matches ImmzProbe).
    private const val TODAY = "2026-08-18"

    private val DATA_BUNDLE =
      """
      {"resourceType":"Bundle","type":"collection","entry":[
        {"resource":{"resourceType":"Patient","id":"measles-inf-9m","gender":"male","birthDate":"2025-11-10"}}
      ]}
      """
        .trimIndent()
  }
}
