package dev.ohs.fhir.workflow.demo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import dev.ohs.fhir.workflow.demo.ui.App

fun main() = application {
  Window(
    onCloseRequest = ::exitApplication,
    title = "kotlin-fhir-workflow demo",
    state = WindowState(size = DpSize(420.dp, 800.dp)),
  ) {
    App()
  }
}
