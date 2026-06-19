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
    override val jvmTestKind: JvmTestKind?,
    override val suiteName: String?,
    override val className: String?,
    override val methodName: String?,
    private val testSource: TestSource?
) : DefaultTestOperationDescriptor(internalJvmTestDescriptor!!, parent), JvmTestOperationDescriptor {








    override val source: TestSource?
        get() = this.testSource
}
