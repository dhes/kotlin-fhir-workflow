package dev.ohs.fhir.workflow.expression

class ExpressionEvaluatorRouter(
  private val fhirPath: ExpressionEvaluator = FhirPathExpressionEvaluator(),
  private val elm: ExpressionEvaluator = ElmExpressionEvaluator(),
) : ExpressionEvaluator {
  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult =
    when (expression) {
      is ProtocolExpression.FhirPath -> fhirPath.evaluate(expression, context)
      is ProtocolExpression.Elm -> elm.evaluate(expression, context)
    }
}
