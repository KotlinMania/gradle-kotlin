/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.resources

import org.gradle.api.GradleException
import org.gradle.internal.exceptions.Contextual
import java.net.URI

/**
 * Generic resource exception that all other resource-related exceptions inherit from.
 */
@Contextual
open class ResourceException : GradleException {
    /**
     * Returns the location of the resource, if known.
     *
     * @return The location, or null if not known.
     */
    val location: URI?

    constructor() {
        location = null
    }

    constructor(message: String?) : super(message) {
        location = null
    }

    constructor(message: String?, cause: Throwable?) : super(message, cause) {
        location = null
    }

    constructor(location: URI?, message: String?) : super(message) {
        this.location = location
    }

    constructor(location: URI?, message: String?, cause: Throwable?) : super(message, cause) {
        this.location = location
    }
}
