/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.resource

import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import java.io.File
import java.io.FileNotFoundException
import java.net.URI

object ResourceExceptions {
    fun readFolder(location: File): ResourceIsAFolderException {
        return ResourceIsAFolderException(location.toURI(), String.format("Cannot read '%s' because it is a folder.", location))
    }

    fun readFailed(location: File, failure: Throwable?): ResourceException {
        return failure(location.toURI(), String.format("Could not read '%s'.", location), failure)
    }

    fun readFailed(displayName: String?, failure: Throwable?): ResourceException {
        return ResourceException(String.format("Could not read %s.", displayName), failure)
    }

    fun readMissing(location: File, failure: Throwable?): MissingResourceException {
        return MissingResourceException(
            location.toURI(),
            String.format("Could not read '%s' as it does not exist.", location),
            if (failure is FileNotFoundException) null else failure
        )
    }

    fun getMissing(location: URI?, failure: Throwable?): MissingResourceException {
        return MissingResourceException(
            location,
            String.format("Could not read '%s' as it does not exist.", location),
            if (failure is FileNotFoundException) null else failure
        )
    }

    fun getMissing(location: URI?): MissingResourceException {
        return MissingResourceException(
            location,
            String.format("Could not read '%s' as it does not exist.", location)
        )
    }

    fun getFailed(location: URI, failure: Throwable?): ResourceException {
        return failure(location, String.format("Could not get resource '%s'.", location), failure)
    }

    fun putFailed(location: URI, failure: Throwable?): ResourceException {
        return failure(location, String.format("Could not write to resource '%s'.", location), failure)
    }

    /**
     * Wraps the given failure, unless it is a ResourceException with the specified location.
     */
    fun failure(location: URI, message: String?, failure: Throwable?): ResourceException {
        if (failure is ResourceException) {
            val resourceException = failure
            if (location == resourceException.location) {
                return resourceException
            }
        }
        return ResourceException(location, message, failure)
    }
}
