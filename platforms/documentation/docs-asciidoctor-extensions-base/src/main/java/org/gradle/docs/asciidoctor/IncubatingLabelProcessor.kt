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
package org.gradle.docs.asciidoctor

import org.asciidoctor.ast.PhraseNode
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.extension.Format
import org.asciidoctor.extension.FormatType
import org.asciidoctor.extension.InlineMacroProcessor
import org.asciidoctor.extension.Name

/**
 * Processes `incubating-label` inline macros to add incubation badges.
 *
 *
 * Usage: `incubating-label:[]`
 *
 *
 * This creates a small badge indicating that something is incubating.
 */
@Name("incubating-label")
@Format(FormatType.SHORT)
class IncubatingLabelProcessor : InlineMacroProcessor() {
    override fun process(parent: StructuralNode?, target: String?, attributes: MutableMap<String?, Any?>?): PhraseNode? {
        val html = "<span class=\"incubating-label\">Incubating</span>"
        return createPhraseNode(parent, "quoted", html, attributes, HashMap<String?, Any?>())
    }
}
