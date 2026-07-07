package dev.ohs.fhir.workflow.activity.resource.request

sealed class Intent(val code: String?) {
  data object PROPOSAL : Intent("proposal")
  data object PLAN : Intent("plan")
  data object ORDER : Intent("order")
  class OTHER(code: String?) : Intent(code)

  override fun toString(): String = code ?: "null"

  companion object {
    fun of(code: String?): Intent = when (code) {
      "proposal" -> PROPOSAL
      "plan" -> PLAN
      "order" -> ORDER
      else -> OTHER(code)
    }
  }
}
