/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events.test.internal

import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.source.TestSource
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor

/**
 * Implementation of the `JvmTestOperationDescriptor` interface.
 */
class DefaultJvmTestOperationDescriptor(
    internalJvmTestDescriptor: InternalJvmTestDescriptor?,
    parent: OperationDescriptor?,
    private val jvmTestKind: JvmTestKind?,
    private val suiteName: String?,
    private val className: String?,
    private val methodName: String?,
    private val testSource: TestSource?
) : DefaultTestOperationDescriptor(internalJvmTestDescriptor, parent), JvmTestOperationDescriptor {
    override fun getJvmTestKind(): JvmTestKind? {
        return this.jvmTestKind
    }

    override fun getSuiteName(): String? {
        return this.suiteName
    }

    override fun getClassName(): String? {
        return this.className
    }

    override fun getMethodName(): String? {
        return this.methodName
    }

    override fun getSource(): TestSource? {
        return this.testSource
    }
}
