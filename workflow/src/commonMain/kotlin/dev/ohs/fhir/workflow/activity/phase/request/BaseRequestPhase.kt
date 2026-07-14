/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.workflow.activity.phase.request

import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.repository.WorkflowRepository

@Suppress("UNCHECKED_CAST")
abstract class BaseRequestPhase<R : CPGRequestResource<*>>(
  private val repository: WorkflowRepository,
  r: R,
  private val phaseName: Phase.PhaseName,
) : Phase.RequestPhase<R> {

  internal var request: R = r.copy() as R

  override fun getRequestResource(): R = request.copy() as R

  override fun getPhaseName(): Phase.PhaseName = phaseName

  override suspend fun suspendPhase(reason: String?): Result<Unit> = runCatching {
    check(request.getStatus() == Status.ACTIVE) {
      "Can't suspend a request with status ${request.getStatusCode()}"
    }
    request.setStatus(Status.ONHOLD, reason)
    repository.update(request.resource)
  }

  override suspend fun resume(): Result<Unit> = runCatching {
    check(request.getStatus() == Status.ONHOLD) {
      "Can't resume a request with status ${request.getStatusCode()}"
    }
    request.setStatus(Status.ACTIVE)
    repository.update(request.resource)
  }

  override suspend fun update(r: R): Result<Unit> = runCatching {
    require(r.getStatus() in AllowedStatusForPhaseStart) { "Status is ${r.getStatusCode()}" }
    repository.update(r.resource)
    request = r
  }

  override suspend fun enteredInError(reason: String?): Result<Unit> = runCatching {
    request.setStatus(Status.ENTEREDINERROR, reason)
    repository.update(request.resource)
  }

  override suspend fun reject(reason: String?): Result<Unit> = runCatching {
    check(request.getStatus() == Status.ACTIVE) {
      "Can't reject a request with status ${request.getStatusCode()}"
    }
    request.setStatus(Status.REVOKED, reason)
    repository.update(request.resource)
  }

  companion object {
    val AllowedStatusForPhaseStart = listOf(Status.DRAFT, Status.ACTIVE)
  }
}
