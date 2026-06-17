/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api

import java.lang.annotation.ElementType
import javax.annotation.Nonnull
import javax.annotation.meta.TypeQualifierDefault

/**
 * Marks a type or a whole package as providing a non-null API by default.
 *
 * All parameter and return types are assumed to be [Nonnull] unless specifically marked as [javax.annotation.Nullable].
 *
 * All types of an annotated package inherit the package rule.
 * Subpackages do not inherit nullability rules and must be annotated.
 *
 * @since 4.2
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Nonnull
@TypeQualifierDefault(ElementType.METHOD, ElementType.PARAMETER)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Suppress("unused")
@Deprecated(
    """Deprecated in Gradle 9 for removal in Gradle 10.
  Prefer JSpecify annotations such as {@link org.jspecify.annotations.NullMarked} and {@link org.jspecify.annotations.Nullable}.
  Note that you can also still use JSR305 annotations such as {@link javax.annotation.Nonnull} and {@link javax.annotation.Nullable}."""
)
annotation class NonNullApi
