package dev.ohs.fhir.workflow.expression

sealed class EvaluationResult {
  data class Bool(val value: Boolean) : EvaluationResult()
  data class Values(val value: List<Any>) : EvaluationResult()
  data class Failure(val message: String) : EvaluationResult()

  fun asBoolean(): Boolean? = (this as? Bool)?.value
  fun asValues(): List<Any> = (this as? Values)?.value ?: emptyList()
}
