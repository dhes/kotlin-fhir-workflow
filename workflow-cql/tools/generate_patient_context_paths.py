#!/usr/bin/env python3
"""Generates R4PatientContextPaths.kt: for each FHIR R4 resource type, the element that links it to
its patient, by the rule of cqframework's HAPI-based R4FhirModelResolver.getContextPath, and the
search parameter on that element.

The rule: four fixed cases (MedicationStatement and QuestionnaireResponse: subject; Task: for;
Coverage: beneficiary); otherwise the first element, in definition order and searching backbone
elements depth first, whose only type is Reference with Patient among its target profiles.
Choice elements are not searched.

The search parameter is one whose expression is exactly that element, such as `subject` for
Condition.subject, not `patient`, whose expression is Condition.subject.where(resolve() is Patient):
`patient` when there is one, otherwise the first by name.

Usage: generate_patient_context_paths.py <hl7.fhir.r4.core 4.0.1 package directory> <output .kt>
"""
import json
import os
import sys

PATIENT = "http://hl7.org/fhir/StructureDefinition/Patient"
FIXED = {
    "Coverage": "beneficiary",
    "MedicationStatement": "subject",
    "QuestionnaireResponse": "subject",
    "Task": "for",
}


def resource_definitions(package):
    for name in sorted(os.listdir(package)):
        if not (name.startswith("StructureDefinition-") and name.endswith(".json")):
            continue
        with open(os.path.join(package, name)) as f:
            sd = json.load(f)
        if (
            sd.get("kind") == "resource"
            and sd.get("derivation") == "specialization"
            and not sd.get("abstract")
        ):
            yield sd


def context_path(sd):
    elements = sd["snapshot"]["element"]
    children = {}
    for e in elements:
        parent, _, _ = e["path"].rpartition(".")
        if parent:
            children.setdefault(parent, []).append(e)

    def search(element, visited):
        name = element["path"].rpartition(".")[2]
        visited.add(name)
        if name.endswith("[x]"):
            return None
        types = element.get("type", [])
        if len(types) == 1 and types[0]["code"] == "Reference":
            return name if PATIENT in types[0].get("targetProfile", []) else None
        if len(types) == 1 and types[0]["code"] == "BackboneElement":
            for child in children.get(element["path"], []):
                if child["path"].rpartition(".")[2] in visited:
                    continue
                path = search(child, visited)
                if path is not None:
                    return f"{name}.{path}"
        return None

    for child in children.get(sd["type"], []):
        path = search(child, set())
        if path is not None:
            return path
    return None


def search_parameters(package):
    """(type, element path) -> search parameter codes whose expression is exactly that path."""
    codes = {}
    for name in sorted(os.listdir(package)):
        if not (name.startswith("SearchParameter-") and name.endswith(".json")):
            continue
        with open(os.path.join(package, name)) as f:
            sp = json.load(f)
        if sp.get("type") != "reference" or not sp.get("expression"):
            continue
        parts = {part.strip() for part in sp["expression"].split("|")}
        for base in sp["base"]:
            for part in parts:
                if part.startswith(base + "."):
                    codes.setdefault((base, part[len(base) + 1 :]), set()).add(sp["code"])
    return codes


def main(package, output):
    paths = {}
    for sd in resource_definitions(package):
        t = sd["type"]
        path = FIXED.get(t) if t in FIXED else context_path(sd)
        if path is not None and t != "Patient":
            paths[t] = path
    # The repository's license header, as Spotless writes it.
    codes = search_parameters(package)
    params = {}
    for t, path in paths.items():
        found = codes.get((t, path), set())
        if found:
            params[t] = "patient" if "patient" in found else sorted(found)[0]
    with open(os.path.join(os.path.dirname(__file__), "..", "..", "license-header.txt")) as f:
        header = f.read().replace("$YEAR", "2026")
    lines = [
        header.rstrip("\n"),
        "package dev.ohs.fhir.workflow.cql.generated",
        "",
        "// Generated from hl7.fhir.r4.core 4.0.1 by workflow-cql/tools/generate_patient_context_paths.py.",
        "",
        "/** The element that links each FHIR R4 resource type to its patient. */",
        "internal val R4_PATIENT_CONTEXT_PATHS: Map<String, String> =",
        "  mapOf(",
    ]
    lines += [f'    "{t}" to "{p}",' for t, p in sorted(paths.items())]
    lines += [
        "  )",
        "",
        "/**",
        " * The search parameter on the element of [R4_PATIENT_CONTEXT_PATHS], for the types that have one: its",
        " * expression is that element alone, with no `resolve()`.",
        " */",
        "internal val R4_PATIENT_SEARCH_PARAMETERS: Map<String, String> =",
        "  mapOf(",
    ]
    lines += [f'    "{t}" to "{p}",' for t, p in sorted(params.items())]
    lines += ["  )", ""]
    with open(output, "w") as f:
        f.write("\n".join(lines))
    print(f"{len(paths)} resource types, {len(params)} with a search parameter", file=sys.stderr)
    for t in sorted(set(paths) - set(params)):
        print(f"  no search parameter: {t}.{paths[t]}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
