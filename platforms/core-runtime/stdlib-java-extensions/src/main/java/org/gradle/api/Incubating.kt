/*
 * Copyright 2012 the original author or authors.
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

/**
 * Indicates that a feature is incubating. This means that the feature is currently a work-in-progress and may
 * change at any time.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@java.lang.annotation.Target(
    java.lang.annotation.ElementType.TYPE,
    java.lang.annotation.ElementType.ANNOTATION_TYPE,
    java.lang.annotation.ElementType.CONSTRUCTOR,
    java.lang.annotation.ElementType.FIELD,
    java.lang.annotation.ElementType.METHOD,
    java.lang.annotation.ElementType.PACKAGE
)
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class Incubating
