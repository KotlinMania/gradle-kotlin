/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import java.util.Optional

interface RepositoryDisabler {
    fun isDisabled(repositoryId: String): Boolean

    /**
     * Gets the reason why the repository was disabled, if it was disabled.
     *
     * @param repositoryId The id of the repository to check
     * @return the reason why the repository was disabled, if it was disabled; otherwise, an empty [Optional]
     */
    fun getDisabledReason(repositoryId: String): Optional<Throwable>?

    /**
     * Attempts to disable the repository with the given id, recording the exception causing it to be disabled, if
     * that exception is deemed critical.
     *
     * @param repositoryId the id of the repository to disable
     * @param throwable the reason why the repository is being disabled
     * @param retriesExceeded `true` if the maximum number of retries for the repository has been exceeded
     * @return `true` if the repository is now disabled, `false` if it was already disabled or could not be disabled
     * (**Be sure to note the ambiguity in this value**)
     * @implSpec implementations **MUST** return `false` if the repository is not disabled by this call
     */
    fun tryDisableRepository(repositoryId: String, throwable: Throwable, retriesExceeded: Boolean): Boolean

    enum class NoOpDisabler : RepositoryDisabler {
        INSTANCE;

        override fun isDisabled(repositoryId: String): Boolean {
            return false
        }

        override fun getDisabledReason(repositoryId: String): Optional<Throwable> {
            return Optional.empty<Throwable>()
        }

        override fun tryDisableRepository(repositoryId: String, throwable: Throwable, retriesExceeded: Boolean): Boolean {
            return false
        }
    }
}
