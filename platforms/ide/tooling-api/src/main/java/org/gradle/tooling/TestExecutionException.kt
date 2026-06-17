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
package org.gradle.tooling

import org.gradle.api.Incubating

/**
 * Thrown when the [TestLauncher] cannot run tests, or when one or more tests fail.
 *
 * @since 2.6
 */
class TestExecutionException : GradleConnectionException {
    constructor(message: String?, throwable: Throwable?) : super(message, throwable)

    /**
     * Constructor.
     *
     * @since 8.12
     */
    @Incubating
    constructor(message: String?, throwable: Throwable?, failures: Supplier<MutableList<Failure?>?>?) : super(message, throwable, failures)

    constructor(message: String?) : super(message)
}
