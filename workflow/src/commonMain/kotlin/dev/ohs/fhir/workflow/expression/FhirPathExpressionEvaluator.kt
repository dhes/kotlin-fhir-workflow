package dev.ohs.fhir.workflow.expression

import dev.ohs.fhir.fhirpath.FhirPathEngine

/** Reference [ExpressionEvaluator]: evaluates `text/fhirpath` expressions via [FhirPathEngine]. */
class FhirPathExpressionEvaluator(
  private val engine: FhirPathEngine = FhirPathEngine.forR4(),
) : ExpressionEvaluator {

  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult {
    if (expression !is ProtocolExpression.FhirPath) {
      return EvaluationResult.Failure("FhirPathExpressionEvaluator cannot evaluate $expression")
    }
    return try {
      val result: Collection<Any> =
        engine.evaluateExpression(expression.expression, context.subject, context.variables)
      val list = result.toList()
      val single = list.singleOrNull()
      when (val value = single?.let(::coerceBoolean)) {
        null -> EvaluationResult.Values(list)
        else -> EvaluationResult.Bool(value)
      }
    } catch (t: Throwable) {
      EvaluationResult.Failure(t.message ?: "FHIRPath evaluation failed: ${expression.expression}")
    }
  }

  private fun coerceBoolean(value: Any): Boolean? =
    when (value) {
      is Boolean -> value
      is dev.ohs.fhir.model.r4.Boolean -> value.value
      else -> null
    }
}
