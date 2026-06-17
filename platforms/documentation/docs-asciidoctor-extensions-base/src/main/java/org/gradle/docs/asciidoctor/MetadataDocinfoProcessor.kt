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
package org.gradle.docs.asciidoctor

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor

/**
 * This processor injects arbitrary HTML &lt;meta&gt; tags from document
 * attributes the pattern "meta_name-$NAME=$CONTENT" or
 * "meta_property-$PROPERTY=$CONTENT".
 *
 * For example the declaration ":meta-name-robots: noindex" would produce:
 * &lt;meta name="robots" content="noindex"&gt;
 *
 * Underscores will be replaced with colons. ":meta-property-og_locale: en_US"
 * would produce &lt;meta property="og:locale" content="en_US"&gt;
 */
class MetadataDocinfoProcessor @JvmOverloads constructor(config: MutableMap<String?, Any?>? = HashMap<String?, Any?>()) : DocinfoProcessor(config) {
    override fun process(document: Document): String {
        val outputHtml = StringBuilder()
        val attributes = document.getAttributes()

        for (attr in attributes.entries) {
            val attributeKey: String = attr.key!!
            if (attributeKey.startsWith(META_NAME)) {
                val name = attributeKey.substring(META_NAME.length).replace("_".toRegex(), ":")
                val content: String? = attr.value.toString()
                outputHtml.append(String.format("<meta name=\"%s\" content=\"%s\">\n", name, content))
            } else if (attributeKey.startsWith(META_PROPERTY)) {
                val name = attributeKey.substring(META_PROPERTY.length).replace("_".toRegex(), ":")
                val content: String? = attr.value.toString()
                outputHtml.append(String.format("<meta property=\"%s\" content=\"%s\">\n", name, content))
            }
        }

        return outputHtml.toString()
    }

    companion object {
        private const val META_NAME = "meta-name-"
        private const val META_PROPERTY = "meta-property-"
    }
}
