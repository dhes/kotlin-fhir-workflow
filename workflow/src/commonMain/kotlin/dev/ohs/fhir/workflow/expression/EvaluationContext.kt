package dev.ohs.fhir.workflow.expression

import dev.ohs.fhir.model.r4.Resource
import kotlinx.datetime.LocalDate

/**
 * Everything an expression needs. `variables` are exposed as %name in FHIRPath.
 * Prefetch is the CALLER's responsibility: the consumer builds `variables`
 * (e.g. %immunizations) before calling $apply.
 */
data class EvaluationContext(
  val subject: Resource,
  val variables: Map<String, Any?> = emptyMap(),
  val today: LocalDate,
)
