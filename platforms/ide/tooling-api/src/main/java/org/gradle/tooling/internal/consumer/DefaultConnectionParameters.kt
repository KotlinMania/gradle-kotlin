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
package org.gradle.tooling.internal.consumer

import org.gradle.util.GradleVersion
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultConnectionParameters private constructor(
    private val projectDir: File?, private val gradleUserHomeDir: File?, private val embedded: Boolean?,
    private val daemonMaxIdleTimeValue: Int?, private val daemonMaxIdleTimeUnits: TimeUnit?, private val daemonBaseDir: File?,
    private val verboseLogging: Boolean, private val searchUpwards: Boolean?, private val distributionBaseDir: File?
) : ConnectionParameters {
    class Builder {
        private var projectDir: File? = null
        private var gradleUserHomeDir: File? = null
        private var embedded: Boolean? = null
        private var daemonMaxIdleTimeValue: Int? = null
        private var daemonMaxIdleTimeUnits: TimeUnit? = null
        private var verboseLogging = false
        private var daemonBaseDir: File? = null
        private var searchUpwards: Boolean? = null
        private var distributionBaseDir: File? = null

        fun setProjectDir(projectDir: File?): Builder {
            this.projectDir = projectDir
            return this
        }

        fun setGradleUserHomeDir(gradleUserHomeDir: File?): Builder {
            this.gradleUserHomeDir = gradleUserHomeDir
            return this
        }

        fun setEmbedded(embedded: Boolean?): Builder {
            this.embedded = embedded
            return this
        }

        fun setDaemonMaxIdleTimeValue(daemonMaxIdleTimeValue: Int?): Builder {
            this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue
            return this
        }

        fun setDaemonMaxIdleTimeUnits(daemonMaxIdleTimeUnits: TimeUnit?): Builder {
            this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits
            return this
        }

        fun setVerboseLogging(verboseLogging: Boolean): Builder {
            this.verboseLogging = verboseLogging
            return this
        }

        fun setSearchUpwards(searchUpwards: Boolean?): Builder {
            this.searchUpwards = searchUpwards
            return this
        }

        fun setDaemonBaseDir(daemonBaseDir: File?): Builder {
            this.daemonBaseDir = daemonBaseDir
            return this
        }

        fun setDistributionBaseDir(distributionBaseDir: File?) {
            this.distributionBaseDir = distributionBaseDir
        }

        fun build(): DefaultConnectionParameters {
            return DefaultConnectionParameters(
                projectDir,
                gradleUserHomeDir,
                embedded,
                daemonMaxIdleTimeValue,
                daemonMaxIdleTimeUnits,
                daemonBaseDir,
                verboseLogging,
                searchUpwards,
                distributionBaseDir
            )
        }
    }

    override fun getProjectDir(): File? {
        return projectDir
    }

    override fun getDaemonBaseDir(): File? {
        return daemonBaseDir
    }

    override fun getGradleUserHomeDir(): File? {
        return gradleUserHomeDir
    }

    override fun isEmbedded(): Boolean? {
        return embedded
    }

    override fun getDaemonMaxIdleTimeValue(): Int? {
        return daemonMaxIdleTimeValue
    }

    override fun getDaemonMaxIdleTimeUnits(): TimeUnit? {
        return daemonMaxIdleTimeUnits
    }

    override fun getConsumerVersion(): String? {
        return GradleVersion.current().getVersion()
    }

    override fun getVerboseLogging(): Boolean {
        return verboseLogging
    }

    override fun isSearchUpwards(): Boolean? {
        return searchUpwards
    }

    override fun getDistributionBaseDir(): File? {
        return distributionBaseDir
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }
}
