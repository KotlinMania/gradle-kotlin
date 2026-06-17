/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import com.google.common.io.Resources
import groovy.text.SimpleTemplateEngine
import groovy.util.CharsetToolkit
import org.gradle.api.GradleException
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets

class SimpleTemplateOperation(templateURL: URL, target: File, bindings: MutableMap<String, TemplateValue>) : TemplateOperation {
    private val templateURL: URL
    private val target: File
    private val bindings: MutableMap<String, TemplateValue>

    init {
        if (templateURL == null) {
            throw BuildInitException("Template URL must not be null")
        }

        if (target == null) {
            throw BuildInitException("Target file must not be null")
        }

        this.templateURL = templateURL
        this.bindings = bindings
        this.target = target
    }

    override fun generate() {
        try {
            GFileUtils.parentMkdirs(target)
            val templateEngine = SimpleTemplateEngine()
            val templateText = Resources.asCharSource(templateURL, CharsetToolkit.getDefaultSystemCharset()).read()
            val template = templateEngine.createTemplate(templateText)
            Files.asCharSink(target, StandardCharsets.UTF_8, FileWriteMode.APPEND).openStream().use { writer ->
                template.make(bindings).writeTo(writer)
            }
        } catch (ex: Exception) {
            throw GradleException("Could not generate file " + target + ".", ex)
        }
    }
}
