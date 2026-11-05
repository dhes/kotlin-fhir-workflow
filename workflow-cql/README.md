# workflow-cql

CQL for kotlin-fhir-workflow's `PlanDefinition/$apply`: an `ExpressionEvaluator` that evaluates CQL
over kotlin-fhir R4 resources with cqframework's CQL compiler and engine (`org.cqframework` 5.5.0).

It is a separate module so that apps that use only FHIRPath don't take on cqframework. `workflow`
does not depend on it.

## Platforms

| Platform    | Status |
|:------------|:-------|
| Android     | Yes    |
| Desktop JVM | Yes    |
| iOS         | No: cqframework publishes no Kotlin/Native artifacts ([cqframework/clinical_quality_language#1899](https://github.com/cqframework/clinical_quality_language/issues/1899)). |
| Wasm        | Not yet: reading kotlin-fhir choice values (such as `Immunization.occurrence[x]`) without reflection needs kotlin-fhir 1.0.0-rc04. |

On Android, the module uses cqframework's JVM artifacts.

## Use

```kotlin
// FHIR 4.0.1 model info and FHIRHelpers: on the JVM and Android, from org.cqframework:quick.
val evaluator =
  FhirCqlEvaluator(
    librarySources = listOf(myLibraryCql, fhirHelpersCql),
    fhirModelInfoXml = fhirModelInfoXml,
    terminologyProvider = InMemoryTerminologyProvider(valueSets),
  )

val operator =
  FhirOperator(
    repository,
    ExpressionEvaluatorRouter(elm = CqlExpressionEvaluator(evaluator, "MyLibrary", repository)),
  )

val carePlan = operator.generateCarePlan(planDefinition, patient, today = today)
```

`$apply` passes `text/cql` expressions to the router's `elm` evaluator. A text that names a
definition of the library gives that definition's value; any other text is evaluated as a CQL
expression.

For each `$apply`, `CqlExpressionEvaluator` reads the patient's resources of the types the library
and the libraries it includes retrieve, from the `WorkflowRepository`, once. It searches each type
by the search parameter on the element that links it to its patient, such as `subject` for
Condition and `patient` for Immunization. It can instead take a function that returns the patient's
resources.

## Contents

- `FhirCqlEvaluator`: compiles or loads CQL libraries and evaluates them for one patient.
- `CqlExpressionEvaluator`: kotlin-fhir-workflow's `ExpressionEvaluator` over `FhirCqlEvaluator`.
- `KotlinFhirToCql`: converts kotlin-fhir R4 resources to CQL engine values that follow the FHIR
  4.0.1 model info.
- `KotlinFhirModelResolver`, `InMemoryRetrieveProvider`, `InMemoryTerminologyProvider`: cqframework's
  `ModelResolver`, `RetrieveProvider` and `TerminologyProvider` over those values.

## Generated code

Two files in `src/commonMain/kotlin/dev/ohs/fhir/workflow/cql/generated/` are generated, and are
not formatted by Spotless. Regenerate them when kotlin-fhir or cqframework's model info changes.

`R4Structures.kt`, the elements of each kotlin-fhir R4 class in model-info order, from cqframework's
`fhir-modelinfo-4.0.1.xml` (`org.cqframework:quick` 5.5.0) and the classes of kotlin-fhir
`fhir-model-r4` 1.0.0-rc03:

```shell
DIR=<an empty directory>
curl -sL -o "$DIR/quick.jar" https://repo1.maven.org/maven2/org/cqframework/quick/5.5.0/quick-5.5.0.jar
curl -sL -o "$DIR/r4.jar" https://repo1.maven.org/maven2/dev/ohs/fhir/fhir-model-r4/1.0.0-rc03/fhir-model-r4-1.0.0-rc03-sources.jar
unzip -q "$DIR/quick.jar" org/hl7/fhir/fhir-modelinfo-4.0.1.xml -d "$DIR"
unzip -q "$DIR/r4.jar" 'commonMain/dev/ohs/fhir/model/r4/*.kt' -d "$DIR"
python3 -I workflow-cql/tools/generate_elements.py "$DIR/org/hl7/fhir/fhir-modelinfo-4.0.1.xml" \
  "$DIR/commonMain/dev/ohs/fhir/model/r4" \
  workflow-cql/src/commonMain/kotlin/dev/ohs/fhir/workflow/cql/generated/R4Structures.kt
```

`R4PatientContextPaths.kt`, the element that links each R4 resource type to its patient, and the
search parameter on it, from the FHIR R4 core package. The paths follow the rule of cqframework's
HAPI-based `R4FhirModelResolver.getContextPath`, and give the same path for every type:

```shell
DIR=<an empty directory>
curl -sL -o "$DIR/r4core.tgz" https://packages.fhir.org/hl7.fhir.r4.core/4.0.1
tar -xzf "$DIR/r4core.tgz" -C "$DIR"
python3 -I workflow-cql/tools/generate_patient_context_paths.py "$DIR/package" \
  workflow-cql/src/commonMain/kotlin/dev/ohs/fhir/workflow/cql/generated/R4PatientContextPaths.kt
```

## Tests

```shell
./gradlew :workflow-cql:desktopTest
```
