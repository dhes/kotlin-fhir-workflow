package dev.ohs.fhir.workflow.activity.phase

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.ref

sealed interface Phase {
  enum class PhaseName { PROPOSAL, PLAN, ORDER, PERFORM }

  fun getPhaseName(): PhaseName

  interface RequestPhase<R : CPGRequestResource<*>> : Phase, ReadOnlyRequestPhase<R> {
    suspend fun update(r: R): Result<Unit>
    suspend fun suspendPhase(reason: String?): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun enteredInError(reason: String?): Result<Unit>
    suspend fun reject(reason: String?): Result<Unit>
  }

  interface EventPhase<E : CPGEventResource<*>> : Phase {
    fun getEventResource(): E
    suspend fun update(e: E): Result<Unit>
    suspend fun suspendPhase(reason: String?): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun enteredInError(reason: String?): Result<Unit>
    suspend fun start(): Result<Unit>
    suspend fun notDone(reason: String?): Result<Unit>
    suspend fun stop(reason: String?): Result<Unit>
    suspend fun complete(): Result<Unit>
  }
}

interface ReadOnlyRequestPhase<R : CPGRequestResource<*>> {
  fun getPhaseName(): Phase.PhaseName
  fun getRequestResource(): R
}

internal fun checkReferencesEqual(a: Reference, b: Reference): Boolean = a.ref == b.ref
