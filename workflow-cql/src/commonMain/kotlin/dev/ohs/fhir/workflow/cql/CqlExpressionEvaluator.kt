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

import dev.ohs.fhir.model.r4.Code as FhirCode
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.cql.generated.R4_PATIENT_SEARCH_PARAMETERS
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.EvaluationResult
import dev.ohs.fhir.workflow.expression.ExpressionEvaluator
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import kotlinx.io.Source
import org.cqframework.cql.cql2elm.LibrarySourceProvider
import org.cqframework.cql.cql2elm.utils.asSource
import org.cqframework.cql.elm.visiting.BaseElmLibraryVisitor
import org.hl7.elm.r1.Element
import org.hl7.elm.r1.Retrieve
import org.hl7.elm.r1.VersionedIdentifier
import org.opencds.cqf.cql.engine.runtime.Boolean as CqlBoolean
import org.opencds.cqf.cql.engine.runtime.ClassInstance
import org.opencds.cqf.cql.engine.runtime.Code as CqlCode
import org.opencds.cqf.cql.engine.runtime.Concept as CqlConcept
import org.opencds.cqf.cql.engine.runtime.Decimal as CqlDecimal
import org.opencds.cqf.cql.engine.runtime.Integer as CqlInteger
import org.opencds.cqf.cql.engine.runtime.List as CqlList
import org.opencds.cqf.cql.engine.runtime.String as CqlString
import org.opencds.cqf.cql.engine.runtime.TemporalHelper
import org.opencds.cqf.cql.engine.runtime.Value
import org.opencds.cqf.cql.engine.util.ZonedDateTime

/**
 * Evaluates CQL expressions for kotlin-fhir-workflow's `$apply`, over one library, for the patient
 * that is each call's subject.
 *
 * @param patientData the resources of the given types that belong to the patient with the given id
 * @param parameters values for the library's parameters
 */
class CqlExpressionEvaluator(
  private val evaluator: FhirCqlEvaluator,
  private val library: String,
  private val patientData: suspend (patientId: String, types: Set<String>) -> List<Resource>,
  private val parameters: Map<String, Value?> = emptyMap(),
) : ExpressionEvaluator {

  /**
   * Reads the patient's resources from [repository], searching each type by the search parameter on
   * the element that links it to its patient, and not reading a type that has none.
   */
  constructor(
    evaluator: FhirCqlEvaluator,
    library: String,
    repository: WorkflowRepository,
    parameters: Map<String, Value?> = emptyMap(),
  ) : this(
    evaluator,
    library,
    { patientId, types ->
      types.flatMap { type ->
        R4_PATIENT_SEARCH_PARAMETERS[type]?.let {
          repository.searchByReferenceParam(type, it, "Patient/$patientId")
        } ?: emptyList()
      }
    },
    parameters,
  )

  private val retrievedTypes: Set<String> by lazy { retrievedTypes(library, mutableSetOf()) }

  private var last: PatientEvaluation? = null

  /**
   * Gives the value of the library definition that [expression] names, or else of [expression]
   * evaluated as CQL, for the patient read once for each [context].
   */
  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult {
    val text =
      (expression as? ProtocolExpression.Elm)?.elmJson
        ?: return EvaluationResult.Failure("Not CQL: $expression")
    return try {
      val patient = patientEvaluation(context)
      val value =
        if (text in patient.definitions) {
          patient.definitions[text]
        } else {
          val name = evaluator.inlineLibraries.nameFor(text, library)
          patient
            .evaluate(name, listOf(InlineCqlLibraries.DEFINITION))[
              InlineCqlLibraries.DEFINITION,
            ]
        }
      result(value)
    } catch (e: Throwable) {
      // Throwable, not Exception: on Wasm, cqframework's CQL engine throws NotImplementedError for
      // Split.
      EvaluationResult.Failure("${e::class.simpleName}: ${e.message}")
    }
  }

  /** The patient's data for [context], read on the first call with that [EvaluationContext]. */
  private suspend fun patientEvaluation(context: EvaluationContext): PatientEvaluation {
    last
      ?.takeIf { it.context === context }
      ?.let {
        return it
      }
    val patient = context.subject as? Patient ?: error("The subject is not a Patient")
    val patientId = patient.id ?: error("The Patient has no id")
    val dateTime = evaluationDateTime(context.today)
    val converter = KotlinFhirToCql(TemporalHelper.zoneToOffset(dateTime.offset))
    val resources = listOf(patient) + patientData(patientId, retrievedTypes - "Patient")
    return PatientEvaluation(context, patientId, resources.map(converter::convert), dateTime).also {
      last = it
    }
  }

  /** The FHIR types that [name] and the libraries it includes, directly or not, retrieve. */
  private fun retrievedTypes(name: String, seen: MutableSet<String>): Set<String> {
    if (!seen.add(name)) return emptySet()
    val elm =
      evaluator.libraryManager.resolveLibrary(VersionedIdentifier().withId(name)).library
        ?: error("Library $name not found")
    val types = mutableSetOf<String>()
    RetrievedTypes.visitLibrary(elm, types)
    elm.includes?.def.orEmpty().mapNotNull { it.path }.forEach { types += retrievedTypes(it, seen) }
    return types
  }

  private inner class PatientEvaluation(
    val context: EvaluationContext,
    private val patientId: String,
    private val resources: List<ClassInstance>,
    private val dateTime: ZonedDateTime,
  ) {
    val definitions: Map<String, Value?> by lazy { evaluate(library, null) }

    fun evaluate(name: String, expressions: List<String>?): Map<String, Value?> =
      evaluator.evaluate(name, patientId, resources, expressions, parameters, dateTime)
  }

  /** Collects the FHIR types a library retrieves. */
  private object RetrievedTypes : BaseElmLibraryVisitor<Unit, MutableSet<String>>() {
    override fun defaultResult(elm: Element, context: MutableSet<String>) = Unit

    override fun visitRetrieve(elm: Retrieve, context: MutableSet<String>) {
      elm.dataType?.let { if (it.getNamespaceURI() == FHIR_MODEL_URI) context += it.getLocalPart() }
      super.visitRetrieve(elm, context)
    }
  }

  private fun result(value: Value?): EvaluationResult =
    when (value) {
      null -> EvaluationResult.Values(emptyList())
      is CqlBoolean -> EvaluationResult.Bool(value.value)
      is CqlList -> EvaluationResult.Values(value.filterNotNull().map(::workflowValue))
      else -> EvaluationResult.Values(listOf(workflowValue(value)))
    }

  /**
   * The value as the type kotlin-fhir-workflow's write-back takes, such as a [Coding] for a Code.
   */
  private fun workflowValue(value: Value): Any =
    when (value) {
      is CqlString -> value.value

      is CqlBoolean -> value.value

      is CqlInteger -> value.value

      is CqlDecimal -> value.value.toDouble()

      is CqlCode -> coding(value)

      is CqlConcept ->
        CodeableConcept(
          coding = value.codes.orEmpty().filterNotNull().map(::coding),
          text = value.display?.let { FhirString(value = it) },
        )

      // A FHIR primitive, such as `id` from `Resource.id`: its value.
      is ClassInstance ->
        if (value.type.getLocalPart().first().isLowerCase()) {
          workflowValue(
            value.elements["value"]
              ?: throw IllegalStateException("FHIR ${value.type.getLocalPart()} with no value")
          )
        } else {
          throw IllegalStateException("No conversion for FHIR ${value.type.getLocalPart()}")
        }

      else -> throw IllegalStateException("No conversion for a CQL ${value::class.simpleName}")
    }

  private fun coding(code: CqlCode) =
    Coding(
      system = code.system?.let { Uri(value = it) },
      version = code.version?.let { FhirString(value = it) },
      code = code.code?.let { FhirCode(value = it) },
      display = code.display?.let { FhirString(value = it) },
    )
}

/**
 * Serves one library per CQL expression, each defining [DEFINITION] as the expression in Patient
 * context, with FHIRHelpers and the given library included.
 */
class InlineCqlLibraries : LibrarySourceProvider {
  private val names = mutableMapOf<Pair<String, String>, String>()
  private val sources = mutableMapOf<String, String>()

  /**
   * The name of the library that defines [expression], with [library] included under its own name.
   */
  fun nameFor(expression: String, library: String): String =
    names.getOrPut(expression to library) {
      val name = "InlineExpression${names.size + 1}"
      sources[name] =
        """
                |library $name
                |using FHIR version '4.0.1'
                |include FHIRHelpers version '4.0.1'
                |include $library called $library
                |context Patient
                |define "$DEFINITION": $expression
                |
      """
          .trimMargin()
      name
    }

  override fun getLibrarySource(libraryIdentifier: VersionedIdentifier): Source? =
    sources[libraryIdentifier.id]?.asSource()

  companion object {
    const val DEFINITION = "Value"
  }
}
