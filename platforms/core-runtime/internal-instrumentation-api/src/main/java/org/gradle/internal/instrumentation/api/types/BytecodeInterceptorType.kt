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
package org.gradle.internal.instrumentation.api.types

enum class BytecodeInterceptorType(
    @JvmField val interceptorMarkerInterface: Class<out FilterableBytecodeInterceptor>,
    @JvmField val interceptorFactoryMarkerInterface: Class<out FilterableBytecodeInterceptorFactory>
) {
    /**
     * An interceptor that is applied to the bytecode always.
     */
    INSTRUMENTATION(
        FilterableBytecodeInterceptor.InstrumentationInterceptor::class.java,
        FilterableBytecodeInterceptorFactory.InstrumentationInterceptorFactory::class.java
    ),

    /**
     * An interceptor that is applied to the bytecode only for upgrades.
     */
    BYTECODE_UPGRADE(
        FilterableBytecodeInterceptor.BytecodeUpgradeInterceptor::class.java,
        FilterableBytecodeInterceptorFactory.BytecodeUpgradeInterceptorFactory::class.java
    ),

    BYTECODE_UPGRADE_REPORT(
        FilterableBytecodeInterceptor.BytecodeUpgradeReportInterceptor::class.java,
        FilterableBytecodeInterceptorFactory.BytecodeUpgradeReportInterceptorFactory::class.java
    )
}
