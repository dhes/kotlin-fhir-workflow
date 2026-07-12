package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import kotlin.uuid.Uuid

/**
 * The perform-phase event for a Task-based flow. FHIR has no distinct Task event resource, so the
 * event is a fresh [Task] (status `ready` -> `in-progress` -> `completed`) `basedOn` the request
 * Task, mirroring [CPGCommunicationEvent]. Uses [TaskEventStatusMapper] because Task's status
 * vocabulary differs from the event pattern.
 */
class CPGTaskEvent(resource: Task) : CPGEventResource<Task>(TaskEventStatusMapper) {

  override var resource: Task = resource

  override fun setStatus(status: EventStatus, reason: String?) {
    resource = resource.copy(
      status = Enumeration(value = Task.TaskStatus.fromCode(mapper.mapStatusToCode(status) ?: "ready")),
      statusReason = reason?.let { CodeableConcept(coding = listOf(Coding(code = Code(value = it)))) },
    )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()
  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }
  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()
  override fun copy(): CPGEventResource<Task> = CPGTaskEvent(resource.copy())

  companion object {
    fun from(request: CPGTaskRequest): CPGTaskEvent {
      val src = request.resource
      return CPGTaskEvent(
        Task(
          id = Uuid.random().toString(),
          status = Enumeration(value = Task.TaskStatus.Ready),
          intent = src.intent,
          code = src.code,
          `for` = src.`for`,
          encounter = src.encounter,
          description = src.description,
          basedOn = listOf(request.asReference()),
        ),
      )
    }
  }
}
