package dev.ohs.fhir.workflow.demo.flow

import kotlin_fhir_workflow.workflow_demo.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads a bundled knowledge artifact, by path relative to `composeResources` (e.g. `files/pd/…`). */
typealias AssetReader = suspend (path: String) -> String

@OptIn(ExperimentalResourceApi::class)
val bundledAssets: AssetReader = { path -> Res.readBytes(path).decodeToString() }
