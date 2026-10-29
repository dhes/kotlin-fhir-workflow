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
| `text/cql`                 | [Expression](https://hl7.org/fhir/R4/metadatatypes.html#Expression)              | ❌      | Routed to `ElmExpressionEvaluator`, which throws `NotImplementedError`.                            |
| `text/cql-identifier`      | [ExpressionLanguage (R5)](https://hl7.org/fhir/R5/valueset-expression-language.html) | ❌  | Rejected: "Unsupported expression language".                                                       |
| `text/cql-expression`      | [ExpressionLanguage (R5)](https://hl7.org/fhir/R5/valueset-expression-language.html) | ❌  | Rejected: "Unsupported expression language".                                                       |
| `application/x-fhir-query` | [Expression](https://hl7.org/fhir/R4/metadatatypes.html#Expression)              | ❌      | Rejected: "Unsupported expression language".                                                       |

## Platform support

| Feature                     | Android | Desktop (JVM) | iOS | wasmJs | Notes                                                    |
|:----------------------------|:--------|:--------------|:----|:-------|:---------------------------------------------------------|
| `PlanDefinition/$apply` with FHIRPath | ✅ | ✅       | ✅   | ✅      |                                                          |
| Tests executed in CI        |         | ✅             | ✅   | ✅      | CI runs desktop, wasmJs and iOS simulator tests.         |

<!-- TODO: confirm the Android column (CI runs the demo's Android unit tests, not the library's); add a CQL row. -->

## Test conformance

<!-- TODO: WHO's immunization test scenarios (WorldHealthOrganization/smart-immunizations, CC0-1.0) under
third_party/, run in the tests, with a table of every scenario that does not pass: scenario, root cause
(Implementation, Test, Specification/Test), tracking issue / PR, note. Same shape as kotlin-fhirpath's
docs/conformance.md. -->
