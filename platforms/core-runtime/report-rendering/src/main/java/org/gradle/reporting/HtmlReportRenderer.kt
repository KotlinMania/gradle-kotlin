/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting

import org.apache.commons.lang3.StringUtils
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.gradle.internal.UncheckedException
import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.io.IOException
import java.io.Writer
import java.net.URL
import java.text.DateFormat
import java.util.Date

class HtmlReportRenderer {
    /**
     * Renders a multi-page HTML report from the given model, into the given directory.
     */
    fun <T> render(model: T?, renderer: ReportRenderer<T?, HtmlReportBuilder?>, outputDirectory: File) {
        try {
            outputDirectory.mkdirs()
            val context = DefaultHtmlReportContext(outputDirectory)
            renderer.render(model, context)
            for (resource in context.resources.values) {
                val destFile = File(outputDirectory, resource.path)
                if (!destFile.exists()) {
                    GFileUtils.copyURLToFile(resource.source, destFile)
                }
            }
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    /**
     * Renders a single page HTML report from the given model, into the given output file.
     */
    fun <T> renderSinglePage(model: T?, renderer: ReportRenderer<T?, HtmlPageBuilder<SimpleHtmlWriter?>?>?, outputFile: File) {
        render<T?>(model, object : ReportRenderer<T?, HtmlReportBuilder?>() {
            override fun render(model: T?, output: HtmlReportBuilder) {
                output.renderHtmlPage<T?>(outputFile.getName(), model, renderer)
            }
        }, outputFile.getParentFile())
    }

    /**
     * Renders a single page HTML report from the given model, into the given output file.
     */
    fun <T> renderRawSinglePage(model: T?, renderer: ReportRenderer<T?, HtmlPageBuilder<Writer?>?>?, outputFile: File) {
        render<T?>(model, object : ReportRenderer<T?, HtmlReportBuilder?>() {
            override fun render(model: T?, output: HtmlReportBuilder) {
                output.renderRawHtmlPage<T?>(outputFile.getName(), model, renderer)
            }
        }, outputFile.getParentFile())
    }

    private class Resource(val source: URL?, val path: String)

    private class DefaultHtmlReportContext(private val outputDirectory: File?) : HtmlReportBuilder {
        private val resources: MutableMap<String?, Resource> = HashMap<String?, Resource>()

        fun addResource(source: URL): Resource {
            val urlString: String? = source.toString()
            var resource = resources.get(urlString)
            if (resource == null) {
                val name = StringUtils.substringAfterLast(source.getPath(), "/")
                var type = StringUtils.substringAfterLast(source.getPath(), ".")
                if (type.equals("png", ignoreCase = true) || type.equals("gif", ignoreCase = true)) {
                    type = "images"
                }
                val path = type + "/" + name
                resource = Resource(source, path)
                resources.put(urlString, resource)
            }
            return resource
        }

        override fun requireResource(source: URL) {
            addResource(source)
        }

        override fun <T> renderHtmlPage(name: String, model: T?, renderer: ReportRenderer<T?, HtmlPageBuilder<SimpleHtmlWriter?>?>) {
            val outputFile = File(outputDirectory, name)
            IoActions.writeTextFile(outputFile, "utf-8", object : ErroringAction<Writer?>() {
                @Throws(Exception::class)
                override fun doExecute(writer: Writer) {
                    val htmlWriter = SimpleHtmlWriter(writer, outputFile.getParentFile().toPath(), "")
                    htmlWriter.startElement("html")
                    renderer.render(model, DefaultHtmlReportContext.DefaultHtmlPageBuilder<SimpleHtmlWriter?>(prefix(name), htmlWriter))
                    htmlWriter.endElement()
                }
            })
        }

        override fun <T> renderRawHtmlPage(name: String, model: T?, renderer: ReportRenderer<T?, HtmlPageBuilder<Writer?>?>) {
            val outputFile = File(outputDirectory, name)
            IoActions.writeTextFile(outputFile, "utf-8", object : ErroringAction<Writer?>() {
                @Throws(Exception::class)
                override fun doExecute(writer: Writer) {
                    renderer.render(model, DefaultHtmlReportContext.DefaultHtmlPageBuilder<Writer?>(prefix(name), writer))
                }
            })
        }

        fun prefix(name: String): String {
            val builder = StringBuilder()
            var pos = 0
            while (pos < name.length) {
                val next = name.indexOf('/', pos)
                if (next < 0) {
                    break
                }
                builder.append("../")
                pos = next + 1
            }
            return builder.toString()
        }

        private inner class DefaultHtmlPageBuilder<D>(private val prefix: String?, private val output: D?) : HtmlPageBuilder<D?> {
            override fun requireResource(source: URL): String {
                val resource = addResource(source)
                return prefix + resource.path
            }

            override fun formatDate(date: Date?): String? {
                return DateFormat.getDateTimeInstance().format(date)
            }

            override fun getOutput(): D? {
                return output
            }
        }
    }
}
