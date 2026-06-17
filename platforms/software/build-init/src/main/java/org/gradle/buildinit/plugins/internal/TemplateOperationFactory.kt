/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.util.GradleVersion
import java.io.File
import java.net.URL
import java.text.DateFormat
import java.util.Date

class TemplateOperationFactory(private val templatepackage: String, private val documentationRegistry: DocumentationRegistry) {
    private val defaultBindings: MutableMap<String, String>

    init {
        this.defaultBindings = loadDefaultBindings()
    }

    private fun loadDefaultBindings(): MutableMap<String, String> {
        val now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        val map: MutableMap<String, String> = LinkedHashMap<String, String>(3)
        map.put("genDate", now!!)
        map.put("genUser", System.getProperty("user.name"))
        map.put("genGradleVersion", GradleVersion.current().toString())
        return map
    }

    fun newTemplateOperation(): TemplateOperationBuilder {
        return TemplateOperationFactory.TemplateOperationBuilder(defaultBindings)
    }

    inner class TemplateOperationBuilder(defaultBindings: MutableMap<String, String>) {
        private var target: File? = null
        private val bindings: MutableMap<String, String> = HashMap<String, String>()
        private var templateUrl: URL? = null

        init {
            this.bindings.putAll(defaultBindings)
        }

        fun withTemplate(relativeTemplatePath: String): TemplateOperationBuilder {
            this.templateUrl = javaClass.getResource(templatepackage + "/" + relativeTemplatePath)
            requireNotNull(templateUrl) { String.format("Could not find template '%s' in classpath.", relativeTemplatePath) }
            return this
        }

        fun withTemplate(templateUrl: URL): TemplateOperationBuilder {
            this.templateUrl = templateUrl
            return this
        }

        fun withTarget(targetFilePath: File): TemplateOperationBuilder {
            this.target = targetFilePath
            return this
        }

        fun withDocumentationBindings(documentationBindings: MutableMap<String, String>): TemplateOperationBuilder {
            for (entry in documentationBindings.entries) {
                bindings.put(entry.key, documentationRegistry.getDocumentationFor(entry.value))
            }
            return this
        }

        fun withBindings(bindings: MutableMap<String, String>): TemplateOperationBuilder {
            this.bindings.putAll(bindings)
            return this
        }

        fun withBinding(name: String, value: String): TemplateOperationBuilder {
            bindings.put(name, value)
            return this
        }

        fun create(): TemplateOperation {
            val entries = bindings.entries
            val wrappedBindings: MutableMap<String, TemplateValue> = HashMap<String, TemplateValue>(entries.size)
            for (entry in entries) {
                requireNotNull(entry.value) { "Null value provided for binding '" + entry.key + "'." }
                wrappedBindings.put(entry.key, TemplateValue(entry.value))
            }
            return SimpleTemplateOperation(templateUrl!!, target!!, wrappedBindings)
        }
    }
}
