# CHW Home Visit demo

An Android demo in the style of a community health worker app's *Home Visit Tasks* screen,
whose Immunizations task is driven end-to-end by **WHO's published SMART Guidelines
immunization content — verbatim**, executing entirely on-device:

1. **WHO's measles CQL runs on the phone.** The
   `IMMZD2DTMeaslesOngoingTransmissionLogic` library (plus its 11-library include
   closure and all 192 ValueSets) is compiled and evaluated on-device by the KMP build of
   cqframework/clinical_quality_language (CQL spec + cql-to-elm translator). The task row's
   status text — *Client is due for MCV1 / MCV2*, *Measles primary series is complete* —
   and the guidance dialog come straight from WHO's `define` statements.
2. **WHO's own PlanDefinition generates the proposal.** `$apply` on
   `IMMZD2DTMeaslesOngoingTransmission` runs through this repo's `FhirOperator`, with the
   PlanDefinition's `text/cql-identifier` conditions routed to the CQL engine via the
   `Expression.reference` seam (`ExpressionEvaluatorRouter` + a demo `ExpressionEvaluator`).
   The generated MedicationRequest — WHO's ActivityDefinition `IMMZD2DTMR` is
   `kind: MedicationRequest, intent: proposal` — is what the ADMINISTER button consumes.
3. **Check-off runs the CPG activity lifecycle.** Tapping ADMINISTER walks the
   `$apply`-generated proposal through this repo's `ActivityFlow`
   (proposal → plan → order → perform → completed), records the administered dose on the
   chart, and re-evaluates the WHO logic — the row flips live.

Three sample charts exercise the decision table: **amara** (10 months, no doses → MCV1
due), **kofi** (16 months, MCV1 given → MCV2 due), **zuri** (24 months, both doses →
series complete). Administering amara's MCV1 and re-running `$apply` yields no further
proposal — the closed loop.

## Prerequisite: local kotlin-fhir build

WHO's documents use `Expression.language: text/cql-identifier`, which published
ohs-foundation/kotlin-fhir (the model library — resource classes; artifact fhir-model)
releases up to rc02 reject at deserialization (issue
[#123](https://github.com/ohs-foundation/kotlin-fhir/issues/123)). This demo runs against
a local build of PR [#128](https://github.com/ohs-foundation/kotlin-fhir/pull/128)
(branch `open-code-123`):

```sh
# in a checkout of ohs-foundation/kotlin-fhir on open-code-123
# (needs local.properties with sdk.dir)
./gradlew -PmavenVersion=1.0.0-open123-SNAPSHOT \
  :fhir-model:publishKotlinMultiplatformPublicationToMavenLocal \
  :fhir-model:publishAndroidPublicationToMavenLocal \
  :fhir-model:publishJvmPublicationToMavenLocal \
  :fhir-model-r4:publishKotlinMultiplatformPublicationToMavenLocal \
  :fhir-model-r4:publishAndroidPublicationToMavenLocal \
  :fhir-model-r4:publishJvmPublicationToMavenLocal \
  :fhir-model-r4b:publishKotlinMultiplatformPublicationToMavenLocal \
  :fhir-model-r4b:publishAndroidPublicationToMavenLocal \
  :fhir-model-r4b:publishJvmPublicationToMavenLocal \
  :fhir-model-r5:publishKotlinMultiplatformPublicationToMavenLocal \
  :fhir-model-r5:publishAndroidPublicationToMavenLocal \
  :fhir-model-r5:publishJvmPublicationToMavenLocal
```

The version catalog pins `ohs-fhir-model = "1.0.0-open123-SNAPSHOT"` and the root build
forces all four fhir-model artifacts to it; both are marked to be dropped when an rc03
with open `Expression.language` ships.

The CQL engine artifacts
(`org.cqframework:{engine,cql-to-elm,engine-fhir}:5.1.0-kmp-fhir-providers-*-SNAPSHOT`)
come from the KMP branch of cqframework/clinical_quality_language
([PR #1815](https://github.com/cqframework/clinical_quality_language/pull/1815)),
resolved from mavenLocal / Sonatype snapshots.

## Run

```sh
./gradlew :chw-demo:installDebug
```

Logcat tag `ChwDemo` shows each evaluation (status booleans, timing, whether `$apply`
produced a proposal) and each ActivityFlow step.

## Known demo shortcuts

- The evaluation date is frozen at `2026-08-18` (`MeaslesEngine.TODAY`) so the sample
  charts age correctly against WHO's age-range logic.
- `WhoPlanApplier.demoScope()` trims the otherwise-verbatim PlanDefinition: it keeps only
  the two vaccination actions and drops their `dynamicValue`s. The kdoc there documents
  the three upstream `:workflow` gaps that block fully-verbatim WHO PDs
  (`text/cql-expression` routing, Terser-style paths in `DynamicValueApplier`,
  engine-value → JSON coercion).
- The CPG activity registry has no Immunization activity yet, so the flow completes as a
  `CPGMedicationRequest` carrying the vaccine code, and the app itself writes the
  `Immunization` chart entry.
- The ActivityFlow repository is in-memory and separate from the JSON chart bundle the
  CQL engine reads; a real app would back both with
  ohs-foundation/kotlin-fhir-engine (persist/sync; artifact fhir-engine).
- `antlr-kotlin-runtime` is forced to 1.0.3 (the CQL engine's version); safe only while
  the demo never evaluates FHIRPath through `:workflow`.
