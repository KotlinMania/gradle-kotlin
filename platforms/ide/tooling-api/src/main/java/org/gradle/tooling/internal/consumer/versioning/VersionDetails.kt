/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.versioning

import org.gradle.util.GradleVersion
import java.io.Serializable

abstract class VersionDetails protected constructor(val version: String?) : Serializable {
    /**
     * Returns true if this provider may support the given model type.
     *
     * Returns false if it is known that the provider does not support the given model type
     * and *should not* be asked to provide it.
     */
    open fun maySupportModel(modelType: Class<*>?): Boolean {
        return false
    }

    open fun supportsEnvironmentVariablesCustomization(): Boolean {
        return false
    }

    open fun supportsRunTasksBeforeExecutingAction(): Boolean {
        return false
    }

    open fun supportsParameterizedToolingModels(): Boolean {
        return false
    }

    open fun supportsRunPhasedActions(): Boolean {
        return false
    }

    open fun supportsPluginClasspathInjection(): Boolean {
        return false
    }

    open fun supportsHelpToolingModel(): Boolean {
        return false
    }

    /**
     * Returns true if this provider correctly implements the protocol contract wrt exceptions thrown on cancel
     */
    open fun honorsContractOnCancel(): Boolean {
        return false
    }

    private open class R26VersionDetails(version: String?) : VersionDetails(version) {
        override fun maySupportModel(modelType: Class<*>?): Boolean {
            return true
        }
    }

    private open class R28VersionDetails(version: String?) : R26VersionDetails(version) {
        override fun supportsPluginClasspathInjection(): Boolean {
            return true
        }
    }

    private open class R35VersionDetails(version: String?) : R28VersionDetails(version) {
        override fun supportsEnvironmentVariablesCustomization(): Boolean {
            return true
        }

        override fun supportsRunTasksBeforeExecutingAction(): Boolean {
            return true
        }
    }

    private open class R44VersionDetails(version: String?) : R35VersionDetails(version) {
        override fun supportsParameterizedToolingModels(): Boolean {
            return true
        }
    }

    private open class R48VersionDetails(version: String?) : R44VersionDetails(version) {
        override fun supportsRunPhasedActions(): Boolean {
            return true
        }
    }

    private open class R51VersionDetails(version: String?) : R48VersionDetails(version) {
        override fun honorsContractOnCancel(): Boolean {
            return true
        }
    }

    private class R94VersionDetails(version: String?) : R51VersionDetails(version) {
        override fun supportsHelpToolingModel(): Boolean {
            return true
        }
    }

    companion object {
        fun from(version: String?): VersionDetails {
            return from(GradleVersion.version(version))
        }

        fun from(version: GradleVersion): VersionDetails {
            if (version.getBaseVersion().compareTo(GradleVersion.version("9.4")) >= 0) {
                return R94VersionDetails(version.getVersion())
            }
            if (version.getBaseVersion().compareTo(GradleVersion.version("5.1")) >= 0) {
                return R51VersionDetails(version.getVersion())
            }
            if (version.getBaseVersion().compareTo(GradleVersion.version("4.8")) >= 0) {
                return R48VersionDetails(version.getVersion())
            }
            if (version.getBaseVersion().compareTo(GradleVersion.version("4.4")) >= 0) {
                return R44VersionDetails(version.getVersion())
            }
            if (version.getBaseVersion().compareTo(GradleVersion.version("3.5")) >= 0) {
                return R35VersionDetails(version.getVersion())
            }
            if (version.getBaseVersion().compareTo(GradleVersion.version("2.8")) >= 0) {
                return R28VersionDetails(version.getVersion())
            }
            return R26VersionDetails(version.getVersion())
        }
    }
}
