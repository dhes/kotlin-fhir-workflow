package dev.ohs.fhir.workflow.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.ohs.fhir.workflow.demo.ui.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport { App() }
}
