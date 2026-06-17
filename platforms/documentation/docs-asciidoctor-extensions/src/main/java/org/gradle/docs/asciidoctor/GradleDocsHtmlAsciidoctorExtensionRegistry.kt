/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.docs.asciidoctor

import org.apache.commons.io.IOUtils
import org.asciidoctor.Asciidoctor
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry
import java.io.IOException

class GradleDocsHtmlAsciidoctorExtensionRegistry : ExtensionRegistry {
    private val headHtml: String?
    private val headerHtml: String?
    private val footerHtml: String?

    init {
        this.headHtml = loadResource(HEAD_HTML_PATH)
        this.headerHtml = loadResource(HEADER_HTML_PATH)
        this.footerHtml = loadResource(FOOTER_HTML_PATH)
    }

    override fun register(asciidoctor: Asciidoctor) {
        val registry = asciidoctor.javaExtensionRegistry()

        registry.docinfoProcessor(NavigationDocinfoProcessor(HashMap<String?, Any?>(), headHtml))

        registry.postprocessor(HeaderInjectingPostprocessor(HashMap<String?, Any?>(), headerHtml))

        val footerOptions: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        footerOptions.put("location", ":footer")
        registry.docinfoProcessor(NavigationDocinfoProcessor(footerOptions, footerHtml))
    }

    private fun loadResource(resourcePath: String): String? {
        try {
            val `in` = javaClass.getResource(resourcePath)
            if (`in` == null) {
                println("Docs Asciidoctor Extension did not find a resource for " + resourcePath)
                return ""
            }
            return IOUtils.toString(`in`, "UTF-8")
        } catch (e: IOException) {
            throw RuntimeException("Could not read HTML file at " + resourcePath)
        }
    }

    companion object {
        private const val HEAD_HTML_PATH = "/head.html"
        private const val HEADER_HTML_PATH = "/header.html"
        private const val FOOTER_HTML_PATH = "/footer.html"
    }
}
