/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.plugins

import com.google.common.io.CharSource
import groovy.text.SimpleTemplateEngine
import org.gradle.api.Transformer
import org.gradle.api.internal.resources.CharSourceBackedTextResource
import org.gradle.api.resources.TextResource
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator
import org.gradle.util.internal.TextUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets

open class DefaultTemplateBasedStartScriptGenerator(
    private val lineSeparator: String?,
    private val bindingFactory: Transformer<MutableMap<String?, String?>?, JavaAppStartScriptGenerationDetails?>,
    private var template: TextResource
) : TemplateBasedScriptGenerator {
    override fun generateScript(details: JavaAppStartScriptGenerationDetails, destination: Writer) {
        try {
            val binding: MutableMap<String?, String?>? = bindingFactory.transform(details)
            val scriptContent = generateStartScriptContentFromTemplate(binding)
            destination.write(scriptContent)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun setTemplate(template: TextResource) {
        this.template = template
    }

    override fun getTemplate(): TextResource {
        return template
    }

    private fun generateStartScriptContentFromTemplate(binding: MutableMap<String?, String?>?): String {
        try {
            getTemplate().asReader().use { reader ->
                val engine = SimpleTemplateEngine()
                val template = engine.createTemplate(reader)
                val output: String? = template.make(binding).toString()
                return TextUtil.convertLineSeparators(output, lineSeparator)!!
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    companion object {
        protected fun utf8ClassPathResource(clazz: Class<*>, filename: String): TextResource {
            return CharSourceBackedTextResource("Classpath resource '" + filename + "'", object : CharSource() {
                @Throws(IOException::class)
                override fun openStream(): Reader {
                    val stream = clazz.getResourceAsStream(filename)
                    checkNotNull(stream) { "Could not find class path resource " + filename + " relative to " + clazz.getName() }
                    return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
                }
            })
        }
    }
}
