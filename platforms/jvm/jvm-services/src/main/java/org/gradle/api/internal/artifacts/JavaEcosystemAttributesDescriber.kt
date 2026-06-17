/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableSet
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.AttributeDescriber
import java.util.Map
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Describes JVM ecosystem related attributes.
 *
 * Methods on this class that accept [Attribute]s and attempt to match them against known values,
 * such as the [.getDescribableAttributes] used by this class, **MUST match on
 * attribute name only, NOT type**.  This allows "desugared" attributes to be described
 * in the same manner.
 */
/* package */
internal class JavaEcosystemAttributesDescriber : AttributeDescriber {
    private val describableAttributes = ImmutableSet.of<Attribute<*>?>(
        Usage.USAGE_ATTRIBUTE,
        Category.CATEGORY_ATTRIBUTE,
        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
        Bundling.BUNDLING_ATTRIBUTE,
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        TargetJvmEnvironment.Companion.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        DocsType.DOCS_TYPE_ATTRIBUTE,
        STATUS_ATTRIBUTE
    )

    /**
     * Checks if the given attribute is describable by this describer.
     *
     * @param attribute the attribute to check
     * @return `true` if the given attribute is describable by this describer; `false` otherwise
     */
    fun isDescribable(attribute: Attribute<*>): Boolean {
        return describableAttributes.stream().anyMatch { describableAttribute: Attribute<*>? -> Companion.haveSameName(attribute, describableAttribute!!) }
    }

    override fun getDescribableAttributes(): ImmutableSet<Attribute<*>?> {
        return describableAttributes
    }

    override fun describeAttributeSet(attributes: MutableMap<Attribute<*>?, *>): String {
        val category: Any? = Companion.extractAttributeValue<Category?>(attributes, Category.CATEGORY_ATTRIBUTE)
        val usage: Any? = Companion.extractAttributeValue<Usage?>(attributes, Usage.USAGE_ATTRIBUTE)
        val le: Any? = Companion.extractAttributeValue<LibraryElements?>(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
        val bundling: Any? = Companion.extractAttributeValue<Bundling?>(attributes, Bundling.BUNDLING_ATTRIBUTE)
        val targetJvmEnvironment: Any? = extractAttributeValue<TargetJvmEnvironment?>(attributes, TargetJvmEnvironment.Companion.TARGET_JVM_ENVIRONMENT_ATTRIBUTE)
        val targetJvm: Any? = Companion.extractAttributeValue<Int?>(attributes, TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
        val docsType: Any? = Companion.extractAttributeValue<DocsType?>(attributes, DocsType.DOCS_TYPE_ATTRIBUTE)
        val status: Any? = extractAttributeValue<String?>(attributes, STATUS_ATTRIBUTE)

        val sb = StringBuilder()

        if (category != null) {
            if (docsType != null && toName(category) == Category.DOCUMENTATION) {
                describeDocsType(docsType, sb)
            } else {
                describeCategory(category, sb)
            }
        } else {
            if (docsType != null && category == null) {
                describeDocsType(docsType, sb)
            } else {
                sb.append("a component")
            }
        }
        if (usage != null) {
            sb.append(" for use during ")
            describeUsage(usage, sb)
        }
        if (status != null) {
            sb.append(", with a ")
            describeStatus(status, sb)
        }
        if (targetJvm != null) {
            sb.append(", compatible with ")
            describeTargetJvm(targetJvm, sb)
        }
        if (le != null) {
            sb.append(", ")
            describeLibraryElements(le, sb)
        }
        if (targetJvmEnvironment != null) {
            sb.append(", preferably optimized for ")
            describeTargetJvmEnvironment(targetJvmEnvironment, sb)
        }
        if (bundling != null) {
            sb.append(", and ")
            describeBundling(bundling, sb)
        }
        processExtraAttributes(attributes, sb)
        return sb.toString()
    }

    private fun processExtraAttributes(attributes: MutableMap<Attribute<*>?, *>, sb: StringBuilder) {
        val describableAttributes: MutableList<Attribute<*>> = attributes.keys.stream()
            .filter { a: Attribute<*>? -> !isDescribable(a!!) }
            .sorted(Comparator.comparing<Attribute<*>?, String?>(Function { obj: Attribute<*>? -> obj!!.getName() }))
            .collect(Collectors.toList())

        if (!describableAttributes.isEmpty()) {
            sb.append(", as well as ")
            var comma = false
            for (attribute in describableAttributes) {
                if (comma) {
                    sb.append(", ")
                }
                describeGenericAttribute(sb, attribute, Companion.extractAttributeValue(attributes, attribute))
                comma = true
            }
        }
    }

    override fun describeMissingAttribute(attribute: Attribute<*>, consumerValue: Any): String? {
        val sb = StringBuilder()
        if (haveSameName(Usage.USAGE_ATTRIBUTE, attribute)) {
            sb.append("its usage (required ")
            describeUsage(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(TargetJvmEnvironment.Companion.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, attribute)) {
            sb.append("its target Java environment (preferred optimized for ")
            describeTargetJvmEnvironment(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("its target Java version (required compatibility with ")
            describeTargetJvm(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(Category.CATEGORY_ATTRIBUTE, attribute)) {
            sb.append("its component category (required ")
            describeCategory(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            sb.append("how its dependencies are found (required ")
            describeBundling(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements (required them ")
            describeLibraryElements(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(DocsType.DOCS_TYPE_ATTRIBUTE, attribute)) {
            sb.append("the documentation type (required ")
            describeDocsType(consumerValue, sb)
            sb.append(")")
        } else if (haveSameName(STATUS_ATTRIBUTE, attribute)) {
            sb.append("its status (required ")
            describeStatus(consumerValue, sb)
            sb.append(")")
        } else {
            return null
        }
        return sb.toString()
    }

    override fun describeExtraAttribute(attribute: Attribute<*>, producerValue: Any): String {
        val sb = StringBuilder()
        if (haveSameName(Usage.USAGE_ATTRIBUTE, attribute)) {
            describeUsage(producerValue, sb)
        } else if (haveSameName(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, attribute)) {
            sb.append("compatibility with ")
            describeTargetJvm(producerValue, sb)
        } else if (haveSameName(Category.CATEGORY_ATTRIBUTE, attribute)) {
            describeCategory(producerValue, sb)
        } else if (haveSameName(DocsType.DOCS_TYPE_ATTRIBUTE, attribute)) {
            describeDocsType(producerValue, sb)
        } else if (haveSameName(STATUS_ATTRIBUTE, attribute)) {
            describeStatus(producerValue, sb)
        } else if (haveSameName(Bundling.BUNDLING_ATTRIBUTE, attribute)) {
            describeBundling(producerValue, sb)
        } else if (haveSameName(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute)) {
            sb.append("its elements ")
            describeLibraryElements(producerValue, sb)
        } else {
            describeGenericAttribute(sb, attribute, producerValue)
        }
        return sb.toString()
    }

    companion object {
        private val STATUS_ATTRIBUTE = Attribute.of<String?>("org.gradle.status", String::class.java)

        private fun describeStatus(status: Any?, sb: StringBuilder) {
            sb.append(toName(status)).append(" status")
        }

        private fun <T> extractAttributeValue(attributes: MutableMap<Attribute<*>?, *>, attribute: Attribute<T?>): Any? {
            return attributes.entries.stream()
                .filter { e: MutableMap.MutableEntry<Attribute<*>?, Any?>? -> Companion.haveSameName(e!!.key!!, attribute) }
                .findFirst()
                .map { Map.Entry.value }
                .orElse(null)
        }

        private fun describeGenericAttribute(sb: StringBuilder, attribute: Attribute<*>, value: Any?) {
            sb.append("attribute '").append(attribute.getName()).append("' with value '").append(value).append("'")
        }

        private fun describeBundling(bundling: Any?, sb: StringBuilder) {
            val name: String = toName(bundling)
            when (name) {
                Bundling.EXTERNAL -> sb.append("its dependencies declared externally")
                Bundling.EMBEDDED -> sb.append("its dependencies bundled (fat jar)")
                Bundling.SHADOWED -> sb.append("its dependencies repackaged (shadow jar)")
                else -> sb.append("its dependencies found as '").append(name).append("'")
            }
        }

        private fun describeLibraryElements(le: Any?, sb: StringBuilder) {
            val name: String = toName(le)
            when (name) {
                LibraryElements.JAR -> sb.append("packaged as a jar")
                LibraryElements.CLASSES -> sb.append("preferably in the form of class files")
                LibraryElements.RESOURCES -> sb.append("preferably only the resources files")
                LibraryElements.CLASSES_AND_RESOURCES -> sb.append("preferably not packaged as a jar")
                else -> sb.append("with the library elements '").append(name).append("'")
            }
        }

        @Suppress("deprecation")
        private fun describeUsage(usage: Any?, sb: StringBuilder) {
            val str: String = toName(usage)
            when (str) {
                Usage.JAVA_API -> sb.append("compile-time")
                Usage.JAVA_RUNTIME -> sb.append("runtime")
                else -> sb.append("'").append(str).append("'")
            }
        }

        private fun describeTargetJvm(targetJvm: Any, sb: StringBuilder) {
            if (targetJvm == Int.MAX_VALUE) {
                sb.append("any Java version")
            } else {
                sb.append("Java ").append(targetJvm)
            }
        }

        private fun describeTargetJvmEnvironment(targetJvmEnvironment: Any?, sb: StringBuilder) {
            val name: String = toName(targetJvmEnvironment)
            when (name) {
                TargetJvmEnvironment.Companion.STANDARD_JVM -> sb.append("standard JVMs")
                TargetJvmEnvironment.Companion.ANDROID -> sb.append("Android")
                else -> sb.append(name)
            }
        }

        private fun describeCategory(category: Any?, sb: StringBuilder) {
            val name: String = toName(category)
            when (name) {
                Category.LIBRARY -> sb.append("a library")
                Category.REGULAR_PLATFORM -> sb.append("a platform")
                Category.ENFORCED_PLATFORM -> sb.append("an enforced platform")
                Category.DOCUMENTATION -> sb.append("documentation")
                else -> sb.append("a component of category '").append(name).append("'")
            }
        }

        private fun describeDocsType(docsType: Any?, sb: StringBuilder) {
            val name: String = toName(docsType)
            when (name) {
                DocsType.JAVADOC -> sb.append("javadocs")
                DocsType.SOURCES -> sb.append("sources")
                DocsType.USER_MANUAL -> sb.append("a user manual")
                DocsType.SAMPLES -> sb.append("samples")
                DocsType.DOXYGEN -> sb.append("doxygen documentation")
                else -> sb.append("documentation of type '").append(name).append("'")
            }
        }

        private fun toName(attributeValue: Any?): String {
            return if (attributeValue is Category) (attributeValue as Named).getName() else attributeValue.toString()
        }

        /**
         * Checks if two attributes have the same name.
         *
         * @param a first attribute to compare
         * @param b second attribute to compare
         * @return `true` if the two attributes have the same name; `false` otherwise
         */
        private fun haveSameName(a: Attribute<*>, b: Attribute<*>): Boolean {
            return a.getName() == b.getName()
        }
    }
}
