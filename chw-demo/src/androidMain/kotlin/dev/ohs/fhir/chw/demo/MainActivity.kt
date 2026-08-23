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
package dev.ohs.fhir.chw.demo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CHW Home Visit demo: an Ona-style task list whose Immunizations row is computed on-device
 * by WHO's published measles CQL (MeaslesEngine). Checking the task off runs the CPG activity
 * lifecycle through the :workflow module (ImmunizationTaskFlow), records the administered
 * dose on the chart, and re-evaluates the WHO logic to update the row.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        var engine by remember { mutableStateOf<MeaslesEngine?>(null) }
        var engineError by remember { mutableStateOf<String?>(null) }
        var selected by remember { mutableStateOf(DEMO_PATIENTS.first()) }
        // Doses administered during this visit, as chart bundle entries per patient.
        var visitDoses by remember { mutableStateOf(mapOf<String, List<String>>()) }
        var statuses by remember { mutableStateOf(mapOf<String, MeaslesEngine.MeaslesStatus>()) }
        var administerProgress by
          remember { mutableStateOf<List<ImmunizationTaskFlow.Step>?>(null) }
        val taskFlow = remember { ImmunizationTaskFlow() }
        val scope = rememberCoroutineScope()

        fun bundleJson() = patientBundleJson(visitDoses.values.flatten())

        LaunchedEffect(Unit) {
          try {
            engine = withContext(Dispatchers.Default) { MeaslesEngine.create(assets) }
          } catch (t: Throwable) {
            Log.e(TAG, "engine init failed", t)
            engineError = "WHO CQL failed to load: ${t.message?.take(120)}"
          }
        }

        // Evaluate the selected patient once the engine is up; cache until the chart changes.
        LaunchedEffect(engine, selected) {
          val e = engine ?: return@LaunchedEffect
          if (selected.id in statuses) return@LaunchedEffect
          val bundle = bundleJson()
          val status = withContext(Dispatchers.Default) { e.evaluate(selected.id, bundle) }
          Log.i(
            TAG,
            "[${selected.id}] due MCV1=${status.dueMcv1} due MCV2=${status.dueMcv2} " +
              "complete=${status.seriesComplete} (${status.evalMillis} ms)",
          )
          statuses = statuses + (selected.id to status)
        }

        fun administer(dose: String) {
          val e = engine ?: return
          val patient = selected
          scope.launch {
            administerProgress = emptyList()
            try {
              withContext(Dispatchers.Default) {
                taskFlow.administer(patient.id, dose) { step ->
                  Log.i(TAG, "[${patient.id}] $dose flow: ${step.name}")
                  administerProgress = (administerProgress ?: emptyList()) + step
                  delay(400)
                }
              }
              val doseNumber =
                (BASE_DOSE_COUNTS[patient.id] ?: 0) + (visitDoses[patient.id]?.size ?: 0) + 1
              visitDoses =
                visitDoses +
                  (patient.id to
                    (visitDoses[patient.id].orEmpty() +
                      administeredDoseEntry(patient.id, doseNumber)))
              val bundle = bundleJson()
              val status = withContext(Dispatchers.Default) { e.evaluate(patient.id, bundle) }
              Log.i(
                TAG,
                "[${patient.id}] after $dose: due MCV1=${status.dueMcv1} " +
                  "due MCV2=${status.dueMcv2} complete=${status.seriesComplete}",
              )
              statuses = statuses + (patient.id to status)
            } catch (t: Throwable) {
              Log.e(TAG, "administer failed", t)
              engineError = "Activity flow failed: ${t.message?.take(120)}"
            } finally {
              administerProgress = null
            }
          }
        }

        val rowState =
          when {
            engineError != null -> ImmunizationRowState.Failed(engineError!!)
            else ->
              statuses[selected.id]?.let {
                ImmunizationRowState.Ready(
                  status = it,
                  administeredThisVisit = selected.id in visitDoses,
                )
              } ?: ImmunizationRowState.Compiling
          }

        HomeVisitScreen(
          selectedPatient = selected,
          onSelectPatient = { selected = it },
          immunizationState = rowState,
          administerProgress = administerProgress,
          onAdminister = ::administer,
        )
      }
    }
  }

  private companion object {
    const val TAG = "ChwDemo"
  }
}
