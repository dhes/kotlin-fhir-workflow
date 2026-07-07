package dev.ohs.fhir.workflow.expression

interface ExpressionEvaluator {
  suspend fun evaluate(expression: ProtocolExpression, context: EvaluationContext): EvaluationResult
}
