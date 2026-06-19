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

class TabsRenderer<T> : ReportRenderer<T, SimpleHtmlWriter>() {
    private val tabs: MutableList<TabDefinition> = ArrayList<TabDefinition>()

    fun add(title: String?, contentRenderer: ReportRenderer<T, SimpleHtmlWriter>) {
        tabs.add(TabDefinition(title, "", contentRenderer))
    }

    fun add(title: String?, tabClass: String?, contentRenderer: ReportRenderer<T, SimpleHtmlWriter>) {
        tabs.add(TabDefinition(title, tabClass, contentRenderer))
    }

    fun clear() {
        tabs.clear()
    }

    @Throws(IOException::class)
    override fun render(model: T?, htmlWriterWriter: SimpleHtmlWriter?) {
        val htmlWriter = htmlWriterWriter!!
        htmlWriter.startElement("div").attribute("class", "tab-container")
        htmlWriter.startElement("ul").attribute("class", "tabLinks")
        for (tab in this.tabs) {
            htmlWriter.startElement("li")
            htmlWriter.startElement("a").attribute("class", tab.tabClass).attribute("href", "#").characters(tab.title).endElement()
            htmlWriter.endElement()
        }
        htmlWriter.endElement()

        for (tab in this.tabs) {
            htmlWriter.startElement("div").attribute("class", "tab")
            htmlWriter.startElement("h2").characters(tab.title).endElement()
            tab.renderer.render(model, htmlWriter)
            htmlWriter.endElement()
        }
        htmlWriter.endElement()
    }

    private inner class TabDefinition(val title: String?, val tabClass: String?, val renderer: ReportRenderer<T, SimpleHtmlWriter>)
}
