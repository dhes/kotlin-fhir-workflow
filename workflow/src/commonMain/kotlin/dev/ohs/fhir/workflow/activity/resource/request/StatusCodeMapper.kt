package dev.ohs.fhir.workflow.activity.resource.request

import dev.ohs.fhir.workflow.activity.resource.request.Status.ACTIVE
import dev.ohs.fhir.workflow.activity.resource.request.Status.COMPLETED
import dev.ohs.fhir.workflow.activity.resource.request.Status.DRAFT
import dev.ohs.fhir.workflow.activity.resource.request.Status.ENTEREDINERROR
import dev.ohs.fhir.workflow.activity.resource.request.Status.ONHOLD
import dev.ohs.fhir.workflow.activity.resource.request.Status.OTHER
import dev.ohs.fhir.workflow.activity.resource.request.Status.REVOKED

interface StatusCodeMapper {
  fun mapCodeToStatus(code: String?): Status
  fun mapStatusToCode(status: Status): String?
}

open class StatusCodeMapperImpl : StatusCodeMapper {
  override fun mapCodeToStatus(code: String?): Status = when (code) {
    "draft" -> DRAFT
    "active" -> ACTIVE
    "on-hold" -> ONHOLD
    "revoked" -> REVOKED
    "completed" -> COMPLETED
    "entered-in-error" -> ENTEREDINERROR
    else -> OTHER(code)
  }

  override fun mapStatusToCode(status: Status): String? = when (status) {
    DRAFT -> "draft"
    ACTIVE -> "active"
    ONHOLD -> "on-hold"
    REVOKED -> "revoked"
    COMPLETED -> "completed"
    ENTEREDINERROR -> "entered-in-error"
    is OTHER -> status.code
  }
}
