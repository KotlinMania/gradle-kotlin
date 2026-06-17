/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile.processing

import java.util.Arrays
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

object ElementUtils {
    const val PACKAGE_TYPE_NAME: String = "package-info"

    fun getTopLevelTypeNames(originatingElements: Array<Element?>): MutableSet<String?> {
        return getTopLevelTypeNames(Arrays.asList<Element?>(*originatingElements))
    }

    fun getTopLevelTypeNames(originatingElements: MutableCollection<out Element>?): MutableSet<String?> {
        if (originatingElements == null || originatingElements.size == 0) {
            return mutableSetOf<String?>()
        }
        if (originatingElements.size == 1) {
            val topLevelTypeName = getTopLevelTypeName(originatingElements.iterator().next())
            return mutableSetOf<String?>(topLevelTypeName)
        }
        val typeNames: MutableSet<String?> = LinkedHashSet<String?>()
        for (element in originatingElements) {
            // TODO: Support for modules
            if (element.getKind().name != "MODULE") {
                val topLevelTypeName = getTopLevelTypeName(element)
                typeNames.add(topLevelTypeName)
            }
        }
        return typeNames
    }

    fun getTopLevelTypeName(originatingElement: Element?): String {
        var current = originatingElement
        var parent = originatingElement
        while (parent != null && parent !is PackageElement) {
            current = parent
            parent = current.getEnclosingElement()
        }
        val name = getElementName(current)
        if (name != null) {
            return name
        }
        throw IllegalArgumentException("Unexpected element " + originatingElement)
    }

    fun getElementName(current: Element?): String? {
        if (current is PackageElement) {
            val packageName = current.getQualifiedName().toString()
            if (packageName.isEmpty()) {
                return PACKAGE_TYPE_NAME
            } else {
                return packageName + "." + PACKAGE_TYPE_NAME
            }
        }
        if (current is TypeElement) {
            val typeElement = current
            return typeElement.getQualifiedName().toString()
        }
        return null // for ModuleElement, which is a top level element, this method currently returns 'null'
    }

    fun getTopLevelType(originatingElement: Element?): Element? {
        var current = originatingElement
        var parent = originatingElement
        while (parent != null && parent !is PackageElement) {
            current = parent
            parent = current.getEnclosingElement()
        }
        return current
    }
}
