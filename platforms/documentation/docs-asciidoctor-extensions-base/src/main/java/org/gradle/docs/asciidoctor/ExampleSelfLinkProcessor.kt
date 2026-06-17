/*
 * Copyright 2023 the original author or authors.
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

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.Treeprocessor

/**
 * Adds self-links to example block titles.
 */
class ExampleSelfLinkProcessor : Treeprocessor() {
    override fun process(document: Document): Document {
        val examples = document.findBy(EXAMPLE_SELECTOR)
        for (example in examples) {
            if (example.hasAttribute("title")) {
                // Using attribute value, since it contains Asciidoc markup, as opposed to getTitle() that returns rendered html
                val title: String? = example.getAttribute("title").toString()
                var exampleId = example.getId()
                if (exampleId == null) {
                    exampleId = IdGenerator.generateId(ID_PREFIX + title)
                    example.setId(exampleId)
                }
                // Using setTitle() instead of setAttribute(), because the latter has no effect
                example.setTitle(String.format("link:#%s[%s]", exampleId, title))
            }
        }
        return document
    }

    companion object {
        private val EXAMPLE_SELECTOR: MutableMap<Any?, Any?> = HashMap<Any?, Any?>()

        // common ID prefix (to avoid trivial cases of clashes where section name has the same id as the example title)
        private const val ID_PREFIX = "ex-"

        init {
            EXAMPLE_SELECTOR.put("context", ":example")
        }
    }
}
