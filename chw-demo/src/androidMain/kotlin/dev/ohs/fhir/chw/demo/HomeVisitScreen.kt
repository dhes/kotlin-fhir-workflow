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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ona CHW-style Home Visit Tasks screen; the Immunizations row is driven by WHO CQL. */

private val OnaBlue = Color(0xFF3B8AD8)
private val CheckGreen = Color(0xFF4CAF50)
private val CircleGray = Color(0xFFBDBDBD)
private val SubtitleGray = Color(0xFF757575)

sealed interface ImmunizationRowState {
  data object Compiling : ImmunizationRowState

  data class Ready(
    val status: MeaslesEngine.MeaslesStatus,
    /** True once a dose was administered during this visit (the task is done for today). */
    val administeredThisVisit: Boolean,
  ) : ImmunizationRowState

  data class Failed(val message: String) : ImmunizationRowState
}

@Composable
fun HomeVisitScreen(
  selectedPatient: DemoPatient,
  onSelectPatient: (DemoPatient) -> Unit,
  immunizationState: ImmunizationRowState,
  administerProgress: List<ImmunizationTaskFlow.Step>?,
  onAdminister: (String) -> Unit,
) {
  Column(Modifier.fillMaxSize().background(Color.White)) {
    HeaderBar()
    PatientChips(selectedPatient, onSelectPatient)
    HorizontalDivider(color = Color(0xFFEEEEEE))
    Column(Modifier.verticalScroll(rememberScrollState())) {
      ImmunizationTaskRow(immunizationState, onAdminister)
      StaticTaskRow("Growth and nutrition")
      StaticTaskRow("Diarrhea / sick visit check")
      StaticTaskRow("Malaria prevention")
      StaticTaskRow("Development")
    }
  }
  if (administerProgress != null) {
    FlowProgressDialog(administerProgress)
  }
}

@Composable
private fun HeaderBar() {
  Column(Modifier.fillMaxWidth().background(OnaBlue).statusBarsPadding()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
      Text("SAVE", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, letterSpacing = 1.sp)
    }
    Text(
      "Home Visit Tasks",
      color = Color.White,
      fontSize = 22.sp,
      modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
    )
  }
}

@Composable
private fun PatientChips(selected: DemoPatient, onSelect: (DemoPatient) -> Unit) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      DEMO_PATIENTS.forEach { patient ->
        FilterChip(
          selected = patient.id == selected.id,
          onClick = { onSelect(patient) },
          label = { Text(patient.label) },
        )
      }
    }
    Text(selected.detail, fontSize = 13.sp, color = SubtitleGray)
  }
}

@Composable
private fun ImmunizationTaskRow(state: ImmunizationRowState, onAdminister: (String) -> Unit) {
  var showDialog by remember { mutableStateOf(false) }
  val subtitle: String
  val done: Boolean
  when (state) {
    is ImmunizationRowState.Compiling -> {
      subtitle = "Compiling WHO measles logic…"
      done = false
    }
    is ImmunizationRowState.Failed -> {
      subtitle = state.message
      done = false
    }
    is ImmunizationRowState.Ready -> {
      val s = state.status
      subtitle =
        when {
          s.seriesComplete -> "Measles primary series is complete"
          s.dueMcv1 -> "Client is due for MCV1"
          s.dueMcv2 -> "Client is due for MCV2"
          else -> "No measles dose due today"
        }
      done = s.seriesComplete || state.administeredThisVisit
    }
  }

  TaskRow(
    title = "Immunizations",
    subtitle = subtitle,
    subtitleColor = if (done) CheckGreen else SubtitleGray,
    done = done,
    loading = state is ImmunizationRowState.Compiling,
    onClick = { if (state is ImmunizationRowState.Ready) showDialog = true },
  )

  val ready = state as? ImmunizationRowState.Ready
  if (showDialog && ready != null) {
    val dueDose =
      when {
        ready.status.dueMcv1 -> "MCV1"
        ready.status.dueMcv2 -> "MCV2"
        else -> null
      }
    GuidanceDialog(
      guidance = ready.status.guidance ?: "No guidance returned.",
      administerDose = dueDose,
      onAdminister = { dose ->
        showDialog = false
        onAdminister(dose)
      },
      onDismiss = { showDialog = false },
    )
  }
}

@Composable
private fun StaticTaskRow(title: String) {
  var done by remember { mutableStateOf(false) }
  TaskRow(title = title, subtitle = null, done = done, onClick = { done = !done })
}

@Composable
private fun TaskRow(
  title: String,
  subtitle: String?,
  done: Boolean,
  subtitleColor: Color = SubtitleGray,
  loading: Boolean = false,
  onClick: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when {
      loading -> CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = OnaBlue)
      done ->
        Icon(
          Icons.Filled.CheckCircle,
          contentDescription = "Done",
          tint = CheckGreen,
          modifier = Modifier.size(36.dp),
        )
      else -> Box(Modifier.size(36.dp).border(2.dp, CircleGray, CircleShape))
    }
    Column(Modifier.padding(start = 20.dp)) {
      Text(title, fontSize = 16.sp)
      if (subtitle != null) {
        Text(subtitle, fontSize = 13.sp, color = subtitleColor)
      }
    }
  }
  HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
private fun GuidanceDialog(
  guidance: String,
  administerDose: String?,
  onAdminister: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      if (administerDose != null) {
        TextButton(onClick = { onAdminister(administerDose) }) { Text("ADMINISTER $administerDose") }
      } else {
        TextButton(onClick = onDismiss) { Text("CLOSE") }
      }
    },
    dismissButton =
      if (administerDose != null) {
        { TextButton(onClick = onDismiss) { Text("CANCEL") } }
      } else {
        null
      },
    title = { Text("WHO guidance", fontWeight = FontWeight.Medium) },
    text = { Text(guidance, style = MaterialTheme.typography.bodyMedium) },
  )
}

/** The CPG activity phases lighting up as ActivityFlow advances the request. */
@Composable
private fun FlowProgressDialog(completed: List<ImmunizationTaskFlow.Step>) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    title = { Text("Recording via ActivityFlow", fontWeight = FontWeight.Medium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ImmunizationTaskFlow.Step.entries.forEach { step ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (step in completed) {
              Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = CheckGreen,
                modifier = Modifier.size(22.dp),
              )
            } else {
              Box(Modifier.size(22.dp).border(2.dp, CircleGray, CircleShape))
            }
            Text(step.label, Modifier.padding(start = 12.dp), fontSize = 14.sp)
          }
        }
      }
    },
  )
}
