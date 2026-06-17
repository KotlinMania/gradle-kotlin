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
package org.gradle.internal.scan

/**
 * Documents that the type or method is referenced by the Develocity plugin,
 * and therefore changes need to be carefully managed. Other plugins like the
 * test-retry or the test-distribution plugin clarify their usage in the [.value].
 * property.
 *
 * @since 4.0
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.CONSTRUCTOR)
annotation class UsedByScanPlugin(
    /**
     * Any clarifying comments about how it is used.
     */
    val value: String = ""
)
