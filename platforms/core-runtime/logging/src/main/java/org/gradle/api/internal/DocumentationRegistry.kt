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
package org.gradle.api.internal

import org.gradle.api.problems.DocLink
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.GradleVersion

/**
 * Locates documentation for various features.
 */
@ServiceScope(Scope.Global::class)
class DocumentationRegistry {
    /**
     * Returns the location of the documentation for the given feature, referenced by id. The location may be local or remote.
     */
    fun getDocumentationFor(id: String): String {
        validateId(id)
        return String.format("%s/userguide/%s.html", BASE_URL, id)
    }

    private fun validateId(id: String) {
        require(!(id.endsWith(".html") || id.endsWith(".adoc"))) {
            "The id '" + id + "' should not end with '.html' or '.adoc'. " +
                    "Provide an id without its file extension to reference documentation."
        }
        require(!id.contains("#")) {
            "The id '" + id + "' should not contain a '#' character. " +
                    "Use getDocumentationFor(id, section) to reference a section anchor in documentation."
        }
    }


    /**
     * Returns the location of the documentation for the given feature, referenced by id and section. The location may be local or remote.
     */
    fun getDocumentationFor(id: String, section: String): String {
        validateSection(section)
        return getDocumentationFor(id) + "#" + section
    }

    private fun validateSection(section: String) {
        require(!section.contains("#")) {
            "The section '" + section + "' should not contain a '#' character. " +
                    "Provide only the section name without a leading '#'."
        }
    }

    fun getDslRefForProperty(clazz: Class<*>, property: String?): String {
        val className = clazz.getName()
        return String.format(DSL_PROPERTY_URL_FORMAT, BASE_URL, className, className, property)
    }

    fun getDslRefForProperty(className: String?, property: String?): String {
        return String.format(DSL_PROPERTY_URL_FORMAT, BASE_URL, className, className, property)
    }

    /**
     * The location of the Kotlin DSL documentation for a Kotlin extension function .
     *
     *
     * The extension function is expected to be defined in the `org.gradle.kotlin.dsl` package.
     */
    fun getKotlinDslRefForExtension(extensionName: String?): String {
        return String.format(KOTLIN_DSL_URL_FORMAT, BASE_URL, "org.gradle.kotlin.dsl/" + extensionName + ".html")
    }

    val sampleIndex: String
        get() = BASE_URL + "/samples"

    fun getSampleFor(id: String?): String {
        return String.format(this.sampleIndex + "/sample_%s.html", id)
    }

    fun getSampleForMessage(id: String?): String {
        return LEARN_MORE_STRING + getSampleFor(id)
    }

    val sampleForMessage: String
        get() = LEARN_MORE_STRING + this.sampleIndex

    fun getDocumentationRecommendationFor(topic: String, id: String): String {
        return getRecommendationString(topic, getDocumentationFor(id))
    }

    fun getDocumentationRecommendationFor(topic: String, id: String, section: String): String {
        return getRecommendationString(topic, getDocumentationFor(id, section))
    }

    fun getDocumentationRecommendationFor(topic: String, docLink: DocLink): String {
        val url = docLink.url
        return getRecommendationString(topic, if (url == null) "<N/A>" else url)
    }


    companion object {
        const val BASE_URL_WITHOUT_VERSION: String = "https://docs.gradle.org/"
        @JvmField
        val BASE_URL: String = BASE_URL_WITHOUT_VERSION + GradleVersion.current().getVersion()
        const val DSL_PROPERTY_URL_FORMAT: String = "%s/dsl/%s.html#%s:%s"
        const val KOTLIN_DSL_URL_FORMAT: String = "%s/kotlin-dsl/gradle/%s"
        const val LEARN_MORE_STRING: String = "Learn more about Gradle by exploring our Samples at "

        const val RECOMMENDATION: String = "For more %s, please refer to %s in the Gradle documentation."

        private fun getRecommendationString(topic: String, url: String?): String {
            return String.format(RECOMMENDATION, topic.trim { it <= ' ' }, url)
        }
    }
}
