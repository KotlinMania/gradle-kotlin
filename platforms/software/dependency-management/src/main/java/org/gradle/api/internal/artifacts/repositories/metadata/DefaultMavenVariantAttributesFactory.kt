/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata

import com.google.common.collect.ImmutableList
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Default implementation of [MavenVariantAttributesFactory], which caches results per input attribute set.
 */
class DefaultMavenVariantAttributesFactory @Inject constructor(private val attributesFactory: AttributesFactory, private val objectInstantiator: NamedObjectInstantiator) :
    MavenVariantAttributesFactory {
    private val concatCache: MutableMap<MutableList<Any>, ImmutableAttributes> = ConcurrentHashMap<MutableList<Any>, ImmutableAttributes>()

    override fun compileScope(original: ImmutableAttributes): ImmutableAttributes {
        val key: MutableList<Any> = ImmutableList.of<Any>(original, Usage.JAVA_API)
        return concatCache.computeIfAbsent(key) { k: MutableList<Any?>? ->
            var result: ImmutableAttributes? = original
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.USAGE_ATTRIBUTE, CoercingStringValueSnapshot(Usage.JAVA_API, objectInstantiator))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.FORMAT_ATTRIBUTE, CoercingStringValueSnapshot(LibraryElements.JAR, objectInstantiator))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(Category.LIBRARY, objectInstantiator))
            result
        }
    }

    override fun runtimeScope(original: ImmutableAttributes): ImmutableAttributes {
        val key: MutableList<Any> = ImmutableList.of<Any>(original, Usage.JAVA_RUNTIME)
        return concatCache.computeIfAbsent(key) { k: MutableList<Any?>? ->
            var result: ImmutableAttributes? = original
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.USAGE_ATTRIBUTE, CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.FORMAT_ATTRIBUTE, CoercingStringValueSnapshot(LibraryElements.JAR, objectInstantiator))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(Category.LIBRARY, objectInstantiator))
            result
        }
    }

    override fun platformWithUsage(original: ImmutableAttributes, usage: String, enforced: Boolean): ImmutableAttributes {
        val componentType = if (enforced) Category.ENFORCED_PLATFORM else Category.REGULAR_PLATFORM
        val key: MutableList<Any> = ImmutableList.of<Any>(original, componentType, usage)
        return concatCache.computeIfAbsent(key) { k: MutableList<Any?>? ->
            var result: ImmutableAttributes? = original
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.USAGE_ATTRIBUTE, CoercingStringValueSnapshot(usage, objectInstantiator))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(componentType, objectInstantiator))
            result
        }
    }

    override fun sourcesVariant(original: ImmutableAttributes): ImmutableAttributes {
        val key: MutableList<Any> = ImmutableList.of<Any>(original, Category.DOCUMENTATION, Usage.JAVA_RUNTIME, DocsType.SOURCES)
        return concatCache.computeIfAbsent(key) { k: MutableList<Any?>? ->
            var result: ImmutableAttributes? = original
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(Category.DOCUMENTATION, objectInstantiator))
            result = attributesFactory.concat<Bundling>(result, Bundling.BUNDLING_ATTRIBUTE, objectInstantiator.named<Bundling>(Bundling::class.java, Bundling.EXTERNAL))
            result = attributesFactory.concat<DocsType>(result, DocsType.DOCS_TYPE_ATTRIBUTE, objectInstantiator.named<DocsType>(DocsType::class.java, DocsType.SOURCES))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.USAGE_ATTRIBUTE, CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator))
            result
        }
    }

    override fun javadocVariant(original: ImmutableAttributes): ImmutableAttributes {
        val key: MutableList<Any> = ImmutableList.of<Any>(original, Category.DOCUMENTATION, Usage.JAVA_RUNTIME, DocsType.JAVADOC)
        return concatCache.computeIfAbsent(key) { k: MutableList<Any?>? ->
            var result: ImmutableAttributes? = original
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(Category.DOCUMENTATION, objectInstantiator))
            result = attributesFactory.concat<Bundling>(result, Bundling.BUNDLING_ATTRIBUTE, objectInstantiator.named<Bundling>(Bundling::class.java, Bundling.EXTERNAL))
            result = attributesFactory.concat<DocsType>(result, DocsType.DOCS_TYPE_ATTRIBUTE, objectInstantiator.named<DocsType>(DocsType::class.java, DocsType.JAVADOC))
            result = attributesFactory.concat<String>(result, MavenVariantAttributesFactory.Companion.USAGE_ATTRIBUTE, CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator))
            result
        }
    }
}
