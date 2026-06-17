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

import org.gradle.internal.html.SimpleHtmlWriter
import java.io.IOException

class TabsRenderer<T> : ReportRenderer<T?, SimpleHtmlWriter?>() {
    private val tabs: MutableList<TabDefinition> = ArrayList<TabDefinition>()

    fun add(title: String?, contentRenderer: ReportRenderer<T?, SimpleHtmlWriter?>) {
        tabs.add(TabsRenderer.TabDefinition(title, "", contentRenderer))
    }

    fun add(title: String?, tabClass: String?, contentRenderer: ReportRenderer<T?, SimpleHtmlWriter?>) {
        tabs.add(TabsRenderer.TabDefinition(title, tabClass, contentRenderer))
    }

    fun clear() {
        tabs.clear()
    }

    @Throws(IOException::class)
    override fun render(model: T?, htmlWriterWriter: SimpleHtmlWriter) {
        htmlWriterWriter.startElement("div").attribute("class", "tab-container")
        htmlWriterWriter.startElement("ul").attribute("class", "tabLinks")
        for (tab in this.tabs) {
            htmlWriterWriter.startElement("li")
            htmlWriterWriter.startElement("a").attribute("class", tab.tabClass).attribute("href", "#").characters(tab.title).endElement()
            htmlWriterWriter.endElement()
        }
        htmlWriterWriter.endElement()

        for (tab in this.tabs) {
            htmlWriterWriter.startElement("div").attribute("class", "tab")
            htmlWriterWriter.startElement("h2").characters(tab.title).endElement()
            tab.renderer.render(model, htmlWriterWriter)
            htmlWriterWriter.endElement()
        }
        htmlWriterWriter.endElement()
    }

    private inner class TabDefinition(val title: String?, private val tabClass: String?, val renderer: ReportRenderer<T?, SimpleHtmlWriter?>)
}
