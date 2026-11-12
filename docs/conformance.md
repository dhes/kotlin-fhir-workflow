# Conformance

This page documents the conformance of the Kotlin FHIR Workflow library against the official HL7
FHIR specifications it implements and against published test cases. The library targets
**FHIR R4 (v4.0.1)**. `PlanDefinition/$apply` is measured against the
[FHIR R4 PlanDefinition $apply operation](https://hl7.org/fhir/R4/plandefinition-operation-apply.html).
How expressions are evaluated, and why FHIRPath comes first, is explained in
[Expression support](expression-support.md).

<!-- TODO: link an android-fhir parity page here, as kotlin-fhir-engine does, if one is added. -->

## Table of Contents

- [Status legend](#status-legend)
- [Operations](#operations)
- [PlanDefinition $apply](#plandefinition-apply)
  - [Operation parameters](#operation-parameters)
  - [Action elements](#action-elements)
  - [Request resources from ActivityDefinition](#request-resources-from-activitydefinition)
  - [Expression languages](#expression-languages)
- [Platform support](#platform-support)
- [Test conformance](#test-conformance)

## Status legend

- ✅ Fully supported and conforms to the specification.
- ⚠️ Partially implemented or diverges from the specification. See *Notes*.
- ❌ Unimplemented.

## Operations

Entry point is
[`FhirOperator.kt`](../workflow/src/commonMain/kotlin/dev/ohs/fhir/workflow/FhirOperator.kt).

| Operation                    | Specification                                                                              | Code                  | Status | Notes                                                    |
|:-----------------------------|:-------------------------------------------------------------------------------------------|:----------------------|:-------|:---------------------------------------------------------|
| `PlanDefinition/$apply`      | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html)                      | `generateCarePlan`    | ⚠️     | See [PlanDefinition $apply](#plandefinition-apply).      |
| `ActivityDefinition/$apply`  | [$apply](https://hl7.org/fhir/R4/activitydefinition-operation-apply.html)                  |                       | ❌      | Used only from a PlanDefinition action's `definition`.   |
| `Measure/$evaluate-measure`  | [$evaluate-measure](https://hl7.org/fhir/R4/measure-operation-evaluate-measure.html)       | `evaluateMeasure`     | ❌      | Throws `NotImplementedError`; requires CQL.              |
| `Library/$evaluate`          |                                                                                            | `evaluateLibrary`     | ❌      | Throws `NotImplementedError`; requires CQL.              |

<!-- TODO: Library/$evaluate is not in FHIR R4 core; link the specification that defines it. -->

## PlanDefinition $apply

Processing is in
[`PlanDefinitionProcessor.kt`](../workflow/src/commonMain/kotlin/dev/ohs/fhir/workflow/processor/PlanDefinitionProcessor.kt).
The result is a `CarePlan` that contains a `RequestGroup` and one request resource per applicable
action with an `ActivityDefinition`.

### Operation parameters

| Parameter         | Specification                                                         | Status | Notes                                                                 |
|:------------------|:----------------------------------------------------------------------|:-------|:----------------------------------------------------------------------|
| `planDefinition`  | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ✅      | A `PlanDefinition`, or its canonical URL resolved by the `CanonicalResolver`. |
| `subject`         | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ⚠️     | One subject resource; the specification allows `1..*`.               |
| `encounter`       | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ❌      |                                                                       |
| `practitioner`    | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ❌      |                                                                       |
| `organization`    | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ❌      |                                                                       |
| `userType`, `userLanguage`, `userTaskContext`, `setting`, `settingContext` | [$apply](https://hl7.org/fhir/R4/plandefinition-operation-apply.html) | ❌ |  |

<!-- TODO: generateCarePlan also takes `variables` and `today`, which are not $apply parameters; say how they map. -->

### Action elements

| Element                  | Specification                                                                 | Status | Notes                                                                                     |
|:-------------------------|:------------------------------------------------------------------------------|:-------|:------------------------------------------------------------------------------------------|
| `action.condition`       | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)                 | ⚠️     | Conditions of kind `applicability` only; `start` and `stop` are not evaluated.            |
| `action.relatedAction`   | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)                 | ⚠️     | Applied as a prerequisite among sibling actions; `relationship` is not read.              |
| `action.definition`      | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)                 | ⚠️     | A canonical to an `ActivityDefinition`; other definitions produce no request.             |
| `action.dynamicValue`    | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)                 | ⚠️     | See [Expression support](expression-support.md#3-this-implementation).                   |
| `action.action`          | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)                 | ✅      | Nested actions become nested `RequestGroup.action`.                                       |
| `action.title`, `action.description` | [PlanDefinition](https://hl7.org/fhir/R4/plandefinition.html)     | ✅      | The title falls back to the `ActivityDefinition`'s.                                       |

<!-- TODO: rows for the action elements not read today (trigger, input, output, timing, participant, type,
groupingBehavior, selectionBehavior, requiredBehavior, precheckBehavior, cardinalityBehavior, transform). -->

### Request resources from ActivityDefinition

| `ActivityDefinition.kind`  | Request resource        | Status | Notes                                                                                  |
|:---------------------------|:------------------------|:-------|:---------------------------------------------------------------------------------------|
| `MedicationRequest`        | `MedicationRequest`     | ⚠️     | Copies `profile`, `priority`, `product` (or `code`) and `dosage`.                      |
| `ServiceRequest`           | `ServiceRequest`        | ⚠️     | Copies `profile` and `code`.                                                           |
| `CommunicationRequest`     | `CommunicationRequest`  | ⚠️     | Copies `profile`.                                                                      |
| any other                  | `Task`                  | ⚠️     | Copies `profile` and `code`; `description` is the action's, else the ActivityDefinition's. |

<!-- TODO: link the specification for instantiating each kind; list ActivityDefinition elements not copied. -->

### Expression languages

Routing by `Expression.language` is in `PlanDefinitionProcessor.kt`; evaluation is behind
[`ExpressionEvaluatorRouter.kt`](../workflow/src/commonMain/kotlin/dev/ohs/fhir/workflow/expression/ExpressionEvaluatorRouter.kt).

| Language                   | Specification                                                                    | Status | Notes                                                                                              |
|:---------------------------|:---------------------------------------------------------------------------------|:-------|:---------------------------------------------------------------------------------------------------|
| `text/fhirpath`            | [Expression](https://hl7.org/fhir/R4/metadatatypes.html#Expression)              | ✅      | Evaluated with kotlin-fhirpath.                                                                    |
| `text/cql`                 | [Expression](https://hl7.org/fhir/R4/metadatatypes.html#Expression)              | ⚠️     | Evaluated by the [`workflow-cql`](../workflow-cql/README.md) module's evaluator when it is the router's `elm` evaluator; the default, `ElmExpressionEvaluator`, throws `NotImplementedError`. |
| `text/cql-identifier`      | [ExpressionLanguage (R5)](https://hl7.org/fhir/R5/valueset-expression-language.html) | ⚠️  | As `text/cql`, with the `library` of the PlanDefinition or ActivityDefinition that holds it.       |
| `text/cql-expression`      | [ExpressionLanguage (R5)](https://hl7.org/fhir/R5/valueset-expression-language.html) | ⚠️  | As `text/cql-identifier`.                                                                          |
| `application/x-fhir-query` | [Expression](https://hl7.org/fhir/R4/metadatatypes.html#Expression)              | ❌      | Rejected: "Unsupported expression language".                                                       |

## Platform support

| Feature                     | Android | Desktop (JVM) | iOS | wasmJs | Notes                                                    |
|:----------------------------|:--------|:--------------|:----|:-------|:---------------------------------------------------------|
| `PlanDefinition/$apply` with FHIRPath | ✅ | ✅       | ✅   | ✅      |                                                          |
| `PlanDefinition/$apply` with CQL (`workflow-cql`) | ⚠️ | ✅ | ❌ | ❌ | Android: builds, not yet run on a device. iOS: cqframework publishes no Kotlin/Native artifacts. wasmJs: not yet. |
| Tests executed in CI        |         | ✅             | ✅   | ✅      | CI runs desktop, wasmJs and iOS simulator tests.         |

<!-- TODO: confirm the Android column (CI runs the demo's Android unit tests, not the library's). -->

## Test conformance

WHO's [SMART Guidelines for immunizations](https://github.com/WorldHealthOrganization/smart-immunizations)
include test cases for their PlanDefinitions: Karate feature files under
`input/testing/features/plandefinition`, each scenario a patient's test bundle, a `Today` date and
what `PlanDefinition/$apply` should return (the number of contained resources and, for decision
tables, the guidance text). The results below are for release
[v1.0.0](https://github.com/WorldHealthOrganization/smart-immunizations/releases/tag/v1.0.0) (commit
8dd61d6934a176793824528391bc2328ebc771f2), which has 1,021 test cases over 138 PlanDefinitions, and
the package `smart.who.int.immunizations` 1.0.0 from packages.fhir.org, which holds those
PlanDefinitions.

[`WhoImmunizationsConformanceTest`](../workflow-cql/src/desktopTest/kotlin/dev/ohs/fhir/workflow/conformance/WhoImmunizationsConformanceTest.kt)
runs them on the JVM (desktop), with WHO's PlanDefinitions as published. Their logic is in CQL, so
the test evaluates it with the [`workflow-cql`](../workflow-cql/README.md) module's evaluator
(cqframework's CQL engine, `org.cqframework` 5.6.1), over the package's libraries and value sets.
WHO's files are not in this repository; the test reads them from the paths in
`WHO_IMMUNIZATIONS_FEATURES` and `WHO_IMMUNIZATIONS_PACKAGE`, and does nothing when those are not
set:

```shell
WHO=<an empty directory, as an absolute path>
git clone --depth 1 --branch v1.0.0 https://github.com/WorldHealthOrganization/smart-immunizations.git "$WHO/smart-immunizations"
mkdir -p "$WHO/package" && curl -sL https://packages.fhir.org/smart.who.int.immunizations/1.0.0 | tar -xz -C "$WHO/package"

WHO_IMMUNIZATIONS_FEATURES="$WHO/smart-immunizations/input/testing/features/plandefinition" \
WHO_IMMUNIZATIONS_PACKAGE="$WHO/package/package" \
  ./gradlew :workflow-cql:desktopTest --tests '*WhoImmunizationsConformanceTest*' --rerun
```

These results include the fix for the dynamicValue paths of
[#9](https://github.com/ohs-foundation/kotlin-fhir-workflow/issues/9); without it, the requests of
most test cases do not decode.

| Test cases                                   | Count | Root cause      | Tracking issue / PR | Note                                                                                                                                  |
|:---------------------------------------------|------:|:----------------|:--------------------|:--------------------------------------------------------------------------------------------------------------------------------------|
| Pass                                         | 1,004 |                 |                     |                                                                                                                                       |
| `IMMZD18SDTPPregnancyStartingWith3Doses/` `DTP69.3`, `DTP70.3`; `IMMZD2DTDTPDelayedOrInterruptedSeries/` `DTP39.3`, `DTP41.3`, `DTP44.4`; `IMMZD2DTDTPOnTimeStart/` `DTP15.3`, `DTP17.3`, `DTP23.4`; `IMMZD18SMeaslesLowTransmission/` `Measles22.1`, `Measles23.3`, `Measles24.3`; `IMMZD18SPneumococcal2DosesWithBoosterDose/` `Pneumococcal14.4`, `21.4`, `22.4`, `24.2`; `IMMZD18SPneumococcal3Doses/` `Pneumococcal46.3` | 16 | Not established |  | The number of contained resources differs from WHO's. cqframework/clinical-reasoning's `PlanDefinitionProcessor` (4.13.0) gives the same number. |
| `IMMZD5DTTyphoidContraindications/Typhoid41.1` |     1 | Test            |                     | `tests-Typhoid41.1-bundle.json` has a MedicationRequest without `medication[x]`, which R4 requires, so kotlin-fhir does not decode it. |

The root cause column says whether a test case fails because of this library (Implementation) or
because of the test case itself (Test).
