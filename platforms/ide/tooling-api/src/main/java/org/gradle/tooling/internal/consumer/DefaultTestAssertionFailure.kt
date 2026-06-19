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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.Failure
import org.gradle.tooling.TestAssertionFailure

open class DefaultTestAssertionFailure(
    message: String?,
    description: String?,
    override val expected: String?,
    override val actual: String?,
    causes: MutableList<out Failure?>?,
    override val className: String?,
    override val stacktrace: String?
) : DefaultFailure(message, description, causes), TestAssertionFailure {







}
