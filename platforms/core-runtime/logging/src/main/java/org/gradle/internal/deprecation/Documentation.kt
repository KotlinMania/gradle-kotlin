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
package org.gradle.internal.deprecation

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.internal.DocLinkInternal
import javax.annotation.CheckReturnValue

abstract class Documentation : DocLinkInternal {
    override fun getConsultDocumentationMessage(): String {
        return String.format(RECOMMENDATION, "information", url)
    }

    private abstract class SerializableDocumentation : Documentation()

    abstract class AbstractBuilder<T> {
        abstract fun withDocumentation(documentation: DocLink?): T?

        /**
         * Allows proceeding without including any documentation reference.
         * Consider using one of the documentation providing methods instead.
         */
        @CheckReturnValue
        fun undocumented(): T? {
            return withDocumentation(null)
        }

        /**
         * Output: See USER_MANUAL_URL for more details.
         */
        @CheckReturnValue
        fun withUserManual(documentationId: String, section: String): T? {
            return withDocumentation(userManual(documentationId, section))
        }

        /**
         * Output: See DSL_REFERENCE_URL for more details.
         */
        @CheckReturnValue
        fun withDslReference(targetClass: Class<*>, property: String): T? {
            return withDocumentation(dslReference(targetClass, property))
        }

        /**
         * Output: Consult the upgrading guide for further information: UPGRADE_GUIDE_URL
         */
        @CheckReturnValue
        fun withUpgradeGuideSection(majorVersion: Int, upgradeGuideSection: String): T? {
            return withDocumentation(upgradeMinorGuide(majorVersion, upgradeGuideSection))
        }
    }

    private open class UserGuide(id: String, private val section: String?) : SerializableDocumentation() {
        private val page: String

        private val topic: String = null

        init {
            this.page = Preconditions.checkNotNull<String>(id)
        }

        override fun getUrl(): String {
            if (section == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page)
            }
            if (topic == null) {
                return DOCUMENTATION_REGISTRY.getDocumentationFor(page, section)
            }
            return DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor(topic, page, section)
        }

        override fun equals(o: Any): Boolean {
            if (o !is UserGuide) {
                return false
            }
            val userGuide = o
            return Objects.equal(page, userGuide.page) && Objects.equal(section, userGuide.section) && Objects.equal(topic, userGuide.topic)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(page, section, topic)
        }
    }

    private class UpgradeGuide(basePath: String, section: String) : UserGuide(basePath, section) {
        override fun getConsultDocumentationMessage(): String {
            return "Consult the upgrading guide for further information: " + getUrl()
        }

        companion object {
            fun forMinorVersion(majorVersion: Int, section: String): UpgradeGuide {
                return UpgradeGuide("upgrading_version_" + majorVersion, section)
            }

            fun forMajorVersion(majorVersion: Int, section: String): UpgradeGuide {
                return UpgradeGuide("upgrading_major_version_" + majorVersion, section)
            }
        }
    }

    private class DslReference(targetClass: Class<*>, property: String) : SerializableDocumentation() {
        private val targetClass: Class<*>
        private val property: String

        init {
            this.targetClass = Preconditions.checkNotNull(targetClass)
            this.property = Preconditions.checkNotNull<String>(property)
        }

        override fun getUrl(): String {
            return DOCUMENTATION_REGISTRY.getDslRefForProperty(targetClass, property)
        }

        override fun equals(o: Any): Boolean {
            if (o !is DslReference) {
                return false
            }
            val that = o
            return Objects.equal(targetClass, that.targetClass) && Objects.equal(property, that.property)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(targetClass, property)
        }
    }

    private class KotlinDslExtensionReference(private val extensionName: String) : SerializableDocumentation() {
        override fun getUrl(): String {
            return DOCUMENTATION_REGISTRY.getKotlinDslRefForExtension(extensionName)
        }

        override fun equals(o: Any): Boolean {
            if (o !is KotlinDslExtensionReference) {
                return false
            }
            val that = o
            return Objects.equal(extensionName, that.extensionName)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(extensionName)
        }
    }

    companion object {
        const val RECOMMENDATION: String = "For more %s, please refer to %s in the Gradle documentation."
        private val DOCUMENTATION_REGISTRY = DocumentationRegistry()

        @JvmStatic
        fun userManual(id: String, section: String): Documentation {
            return UserGuide(id, section)
        }

        @JvmStatic
        fun userManual(id: String): Documentation {
            return UserGuide(id, null)
        }

        @JvmStatic
        fun upgradeMinorGuide(majorVersion: Int, upgradeGuideSection: String): Documentation {
            return UpgradeGuide.Companion.forMinorVersion(majorVersion, upgradeGuideSection)
        }

        @JvmStatic
        fun upgradeMajorGuide(majorVersion: Int, upgradeGuideSection: String): Documentation {
            return UpgradeGuide.Companion.forMajorVersion(majorVersion, upgradeGuideSection)
        }

        fun dslReference(targetClass: Class<*>, property: String): Documentation {
            return DslReference(targetClass, property)
        }

        fun kotlinDslExtensionReference(extensionName: String): Documentation {
            return KotlinDslExtensionReference(extensionName)
        }
    }
}



