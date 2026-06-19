/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.instrumentation.processor.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName

enum class GradleLazyType(className: ClassName?) {
    CONFIGURABLE_FILE_COLLECTION("org.gradle.api.file.ConfigurableFileCollection"),
    FILE_COLLECTION("org.gradle.api.file.FileCollection"),
    DIRECTORY_PROPERTY("org.gradle.api.file.DirectoryProperty"),
    REGULAR_FILE_PROPERTY("org.gradle.api.file.RegularFileProperty"),
    LIST_PROPERTY("org.gradle.api.provider.ListProperty"),
    SET_PROPERTY("org.gradle.api.provider.SetProperty"),
    MAP_PROPERTY("org.gradle.api.provider.MapProperty"),
    PROPERTY("org.gradle.api.provider.Property"),
    PROVIDER("org.gradle.api.provider.Provider"),
    UNSUPPORTED(null) {
        override fun asClassName(): ClassName? {
            throw UnsupportedOperationException("Unsupported type")
        }
    };

    private val className: ClassName?

    constructor(name: String) : this(ClassName.bestGuess(name))

    init {
        this.className = className
    }

    open fun asClassName(): ClassName? {
        return className
    }

    fun isEqualToRawTypeOf(typeName: TypeName?): Boolean {
        var typeName = typeName
        if (typeName is ParameterizedTypeName) {
            typeName = typeName.rawType
        }
        return className == typeName
    }

    companion object {
        fun from(typeName: TypeName): GradleLazyType {
            val binaryName: String?
            if (typeName is ClassName) {
                binaryName = typeName.reflectionName()
            } else if (typeName is ParameterizedTypeName) {
                binaryName = typeName.rawType.reflectionName()
            } else {
                throw UnsupportedOperationException("Cannot get binary name from TypeName: " + typeName.javaClass)
            }
            return from(binaryName)
        }

        fun from(name: String?): GradleLazyType {
            for (gradleType in entries) {
                if (gradleType.className != null && gradleType.className.reflectionName() == name) {
                    return gradleType
                }
            }
            throw UnsupportedOperationException("Unknown Gradle lazy type: " + name)
        }
    }
}
