/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.enterprise

/**
 * Used to signal the end of build to the plugin.
 *
 *
 * Uses a specific listener to guarantee being invoked after user buildFinished callbacks.
 * Expected to be invoked once for a build tree.
 *
 *
 * Implemented by the Enterprise plugin.
 */
interface GradleEnterprisePluginEndOfBuildListener {
    interface BuildResult {
        @get:Deprecated("Use {@link #getBuildFailure()} instead.")
        val failure: Throwable?

        /**
         * The build failure in a more structured form.
         *
         *
         * `null` if the build did not fail.
         *
         * @since 9.0.0
         */
        val buildFailure: BuildFailure?
    }

    /**
     * Information about the build failure.
     *
     * @since 9.0.0
     */
    interface BuildFailure {
        /**
         * The non-empty list of failures.
         *
         * @since 9.0.0
         */
        val failures: MutableList<Failure>?
    }

    fun buildFinished(buildResult: BuildResult)
}
