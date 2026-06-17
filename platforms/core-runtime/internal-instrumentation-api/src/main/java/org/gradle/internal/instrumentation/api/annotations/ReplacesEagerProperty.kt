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
package org.gradle.internal.instrumentation.api.annotations

import kotlin.reflect.KClass

/**
 * Marks that a property replaces an eager property.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class ReplacesEagerProperty(
    /**
     * Overrides original type that will be used for generated code.
     * By default, the original type is determined from the lazy property type, e.g.:
     * Property[T] - original type becomes T (also Property[Integer] becomes Integer and not int)
     * RegularFileProperty - original type becomes File
     * DirectoryProperty - original type becomes File
     * MapProperty[K, V] - original type becomes Map[K, V]
     * ListProperty[T] - original type becomes List[T]
     * ConfigurableFileCollection - original type becomes FileCollection
     */
    val originalType: KClass<*> = DefaultValue::class,
    /**
     * Whether the setter accessor for property was fluent
     */
    val fluentSetter: Boolean = false,
    /**
     * Configuration for binary compatibility check, see [BinaryCompatibility]
     */
    val binaryCompatibility: BinaryCompatibility = BinaryCompatibility.ACCESSORS_REMOVED,
    /**
     * Accessors that are replaced by the property
     */
    val replacedAccessors: Array<ReplacedAccessor> = [],
    /**
     * Deprecation configuration for the replaced accessors
     */
    val deprecation: ReplacedDeprecation = ReplacedDeprecation(),
    /**
     * A custom interception adapter for a property that is used for bytecode upgrade.
     *
     * When this value is set, no other interception will be generated.
     * But [.deprecation] and [.binaryCompatibility] settings are still respected.
     */
    val adapter: KClass<*> = DefaultValue::class
) {
    interface DefaultValue

    enum class BinaryCompatibility {
        /**
         * Gradle binary compatibility check will fail if the accessor was not removed
         */
        ACCESSORS_REMOVED,

        /**
         * Gradle binary compatibility check will fail if the accessor was not kept
         */
        ACCESSORS_KEPT
    }
}
