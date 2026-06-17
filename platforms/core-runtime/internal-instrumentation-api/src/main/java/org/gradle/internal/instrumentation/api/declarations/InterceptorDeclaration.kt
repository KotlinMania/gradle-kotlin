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
package org.gradle.internal.instrumentation.api.declarations

object InterceptorDeclaration {
    const val JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONVENTIONS: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_Conventions"
    const val JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_ConfigCacheJvmBytecode"
    const val GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_ConfigCacheGroovyInterceptors"
    const val JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode"
    const val GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesGroovyInterceptors"
    const val JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesReportJvmBytecode"
    const val GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES_REPORT: String = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesReportGroovyInterceptors"
}
