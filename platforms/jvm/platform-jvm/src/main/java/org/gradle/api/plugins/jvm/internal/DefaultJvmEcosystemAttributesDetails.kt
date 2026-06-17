/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.VerificationType
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.AttributeContainerInternal
import javax.inject.Inject

class DefaultJvmEcosystemAttributesDetails @Inject constructor(private val attributes: AttributeContainerInternal) : JvmEcosystemAttributesDetails {
    override fun apiUsage(): JvmEcosystemAttributesDetails {
        attributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attributes.named<Usage?>(Usage::class.java, Usage.JAVA_API))
        return this
    }

    override fun runtimeUsage(): JvmEcosystemAttributesDetails {
        attributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attributes.named<Usage?>(Usage::class.java, Usage.JAVA_RUNTIME))
        return this
    }

    override fun library(): JvmEcosystemAttributesDetails {
        attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.LIBRARY))
        return this
    }

    override fun library(elementsType: String): JvmEcosystemAttributesDetails {
        library()
        attributes.attribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements?>(LibraryElements::class.java, elementsType))
        return this
    }

    override fun platform(): JvmEcosystemAttributesDetails {
        attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.REGULAR_PLATFORM))
        return this
    }

    override fun documentation(docsType: String): JvmEcosystemAttributesDetails {
        attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.DOCUMENTATION))
        attributes.attribute<DocsType?>(DocsType.DOCS_TYPE_ATTRIBUTE, attributes.named<DocsType?>(DocsType::class.java, docsType))
        return this
    }

    override fun withExternalDependencies(): JvmEcosystemAttributesDetails {
        attributes.attribute<Bundling?>(Bundling.BUNDLING_ATTRIBUTE, attributes.named<Bundling?>(Bundling::class.java, Bundling.EXTERNAL))
        return this
    }

    override fun withEmbeddedDependencies(): JvmEcosystemAttributesDetails {
        attributes.attribute<Bundling?>(Bundling.BUNDLING_ATTRIBUTE, attributes.named<Bundling?>(Bundling::class.java, Bundling.EMBEDDED))
        return this
    }

    override fun withShadowedDependencies(): JvmEcosystemAttributesDetails {
        attributes.attribute<Bundling?>(Bundling.BUNDLING_ATTRIBUTE, attributes.named<Bundling?>(Bundling::class.java, Bundling.SHADOWED))
        return this
    }

    override fun asJar(): JvmEcosystemAttributesDetails {
        attributes.attribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements?>(LibraryElements::class.java, LibraryElements.JAR))
        return this
    }

    override fun withTargetJvmVersion(version: Int): JvmEcosystemAttributesDetails {
        attributes.attribute<Int?>(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, version)
        return this
    }

    override fun preferStandardJVM(): JvmEcosystemAttributesDetails {
        attributes.attribute<TargetJvmEnvironment>(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, attributes.named<TargetJvmEnvironment?>(
                org.gradle.api.attributes.java.TargetJvmEnvironment::class.java,
                org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
            )!!
        )
        return this
    }

    override fun asSources(): JvmEcosystemAttributesDetails {
        attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.VERIFICATION))
        attributes.attribute<VerificationType?>(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, attributes.named<VerificationType?>(VerificationType::class.java, VerificationType.MAIN_SOURCES))
        return this
    }
}
