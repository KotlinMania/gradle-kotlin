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

class ParameterKind {
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class Receiver

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class CallerClassName

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class KotlinDefaultMask

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class VarargParameter

    /**
     * Injects some context from visitor. Not supported for Groovy at the moment.
     *
     * Currently, it's only supported to inject [BytecodeInterceptorFilter].
     */
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class InjectVisitorContext
}
