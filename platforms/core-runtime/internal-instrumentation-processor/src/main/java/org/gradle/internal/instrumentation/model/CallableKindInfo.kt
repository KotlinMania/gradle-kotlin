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
package org.gradle.internal.instrumentation.model

import org.gradle.internal.instrumentation.api.annotations.CallableKind

enum class CallableKindInfo {
    STATIC_METHOD, INSTANCE_METHOD, AFTER_CONSTRUCTOR, GROOVY_PROPERTY_GETTER, GROOVY_PROPERTY_SETTER;

    companion object {
        @JvmStatic
        fun fromAnnotation(annotation: Annotation?): CallableKindInfo {
            if (annotation is CallableKind.StaticMethod) {
                return CallableKindInfo.STATIC_METHOD
            }
            if (annotation is CallableKind.InstanceMethod) {
                return CallableKindInfo.INSTANCE_METHOD
            }
            if (annotation is CallableKind.AfterConstructor) {
                return CallableKindInfo.AFTER_CONSTRUCTOR
            }
            if (annotation is CallableKind.GroovyPropertyGetter) {
                return CallableKindInfo.GROOVY_PROPERTY_GETTER
            }
            if (annotation is CallableKind.GroovyPropertySetter) {
                return CallableKindInfo.GROOVY_PROPERTY_SETTER
            }
            throw IllegalArgumentException("Unexpected annotation " + annotation)
        }
    }
}
