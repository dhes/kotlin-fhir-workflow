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
package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.CompositeDecoder

/**
 * A dynamicValue path in [DynamicValueApplier]'s path contract, and whether it ends at a primitive.
 */
internal data class DynamicValueTarget(val path: String, val isPrimitive: Boolean)

/**
 * Decides the [DynamicValueApplier] path for a FHIRPath dynamicValue [path] on the resource that
 * [descriptor] describes, giving list elements without an index the index 0 and choice elements the
 * name of [value]'s type, or returns [path] unchanged when the descriptor has no element it names.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun dynamicValueTarget(
  descriptor: SerialDescriptor,
  path: String,
  value: Any,
): DynamicValueTarget {
  val parts = path.split(".")
  val segments =
    if (parts.size > 1 && parts[0].firstOrNull()?.isUpperCase() == true) parts.drop(1) else parts
  var current = descriptor
  val rewritten = mutableListOf<String>()
  for ((i, segment) in segments.withIndex()) {
    val index = segment.substringAfter("[", "").substringBefore("]").ifEmpty { null }
    val name =
      elementName(current, segment.substringBefore("["), value.takeIf { i == segments.lastIndex })
        ?: return DynamicValueTarget(path, isPrimitive = false)
    var element = current.getElementDescriptor(current.getElementIndex(name))
    rewritten +=
      when {
        element.kind == StructureKind.LIST -> {
          element = element.getElementDescriptor(0)
          "$name[${index ?: 0}]"
        }

        index != null -> "$name[$index]"

        else -> name
      }
    current = element
  }
  return DynamicValueTarget(rewritten.joinToString("."), current.kind is PrimitiveKind)
}

@OptIn(ExperimentalSerializationApi::class)
private fun elementName(descriptor: SerialDescriptor, name: String, value: Any?): String? {
  if (descriptor.getElementIndex(name) != CompositeDecoder.UNKNOWN_NAME) return name
  val choices =
    descriptor.elementNames.filter {
      it.length > name.length && it.startsWith(name) && it[name.length].isUpperCase()
    }
  return value?.let { choices.firstOrNull { it == name + fhirTypeName(value) } }
    ?: choices.singleOrNull()
}

private fun fhirTypeName(value: Any): String? =
  when (value) {
    is String -> "String"

    is Boolean -> "Boolean"

    is Int,
    is Long -> "Integer"

    is Float,
    is Double -> "Decimal"

    is FhirPathDate -> "Date"

    is FhirPathDateTime -> "DateTime"

    is FhirPathTime -> "Time"

    is FhirPathQuantity -> "Quantity"

    else -> value::class.simpleName
  }
