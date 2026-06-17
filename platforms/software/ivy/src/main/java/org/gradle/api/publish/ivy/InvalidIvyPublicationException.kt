/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.ivy

import org.gradle.api.InvalidUserDataException

/**
 * Thrown when attempting to publish with an invalid [IvyPublication].
 *
 * @since 1.5
 */
class InvalidIvyPublicationException : InvalidUserDataException {
    constructor(publicationName: String?, error: String?) : super(formatMessage(publicationName, error))

    constructor(publicationName: String?, error: String?, cause: Throwable?) : super(formatMessage(publicationName, error), cause)

    companion object {
        private fun formatMessage(publicationName: String?, error: String?): String {
            return String.format("Invalid publication '%s': %s", publicationName, error)
        }
    }
}
