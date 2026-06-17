/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks.internal

import groovy.util.Node
import org.gradle.api.Transformer
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import java.io.File
import java.util.Collections
import java.util.Objects

class VisualStudioFiltersFile(xmlTransformer: XmlTransformer, private val fileLocationResolver: Transformer<String, File>) : XmlPersistableConfigurationObject(xmlTransformer) {
    val defaultResourceName: String
        get() = "default.vcxproj.filters"

    fun addSource(sourceFile: File) {
        getItemGroupForLabel("Sources")
            .appendNode("ClCompile", Collections.singletonMap<String, String>("Include", toPath(sourceFile)))
            .appendNode("Filter", "Source Files")
    }

    fun addHeader(headerFile: File) {
        getItemGroupForLabel("Headers")
            .appendNode("ClInclude", Collections.singletonMap<String, String>("Include", toPath(headerFile)))
            .appendNode("Filter", "Header Files")
    }

    private fun getItemGroupForLabel(label: String): Node {
        return Objects.requireNonNull(
            findFirstChildWithAttributeValue(xml, "ItemGroup", "Label", label),
            "No 'ItemGroup' with attribute 'Label = " + label + "' found"
        )!!
    }

    private fun toPath(file: File): String {
        return fileLocationResolver.transform(file)
    }
}
