package dev.ohs.fhir.workflow.expression

sealed class ProtocolExpression {
  /** text/fhirpath — the reference language. */
  data class FhirPath(val expression: String) : ProtocolExpression()

  /** application/elm+json — evaluated only when an ELM evaluator is available (deferred). */
  data class Elm(val elmJson: String) : ProtocolExpression()
}
