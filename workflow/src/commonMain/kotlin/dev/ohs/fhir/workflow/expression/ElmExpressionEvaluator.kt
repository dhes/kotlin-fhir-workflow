package dev.ohs.fhir.workflow.expression

/**
 * STUB. CQL is authored, compiled to ELM offline, and evaluated here — but no KMP
 * ELM interpreter exists yet, so every call fails cleanly. Swap in a real
 * implementation later without touching the router or the processor.
 */
class ElmExpressionEvaluator : ExpressionEvaluator {
  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult =
    EvaluationResult.Failure(
      "ELM/CQL evaluation is not yet supported on Kotlin Multiplatform. " +
        "Author conditions in text/fhirpath.",
    )
}
