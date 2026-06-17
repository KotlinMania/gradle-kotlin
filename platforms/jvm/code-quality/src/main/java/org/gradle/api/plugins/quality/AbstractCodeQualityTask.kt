/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.Incubating
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.VerificationTask
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import org.gradle.process.JavaForkOptions
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Base class for code quality tasks.
 *
 * @since 8.4
 */
@Incubating
@DisableCachingByDefault(because = "Super-class, not to be instantiated directly")
abstract class AbstractCodeQualityTask @Inject constructor() : SourceTask(), VerificationTask {
    init {
        this.ignoreFailuresProperty.convention(false)
        this.javaLauncher.convention(this.toolchainService.launcherFor(this.objectFactory.newInstance<CurrentJvmToolchainSpec>(CurrentJvmToolchainSpec::class.java)))
    }

    /**
     * {@inheritDoc}
     */
    @ToBeReplacedByLazyProperty
    override fun getIgnoreFailures(): Boolean {
        return this.ignoreFailuresProperty.get()
    }

    /**
     * {@inheritDoc}
     */
    override fun setIgnoreFailures(ignoreFailures: Boolean) {
        this.ignoreFailuresProperty.set(ignoreFailures)
    }

    @get:Internal
    abstract val ignoreFailuresProperty: Property<Boolean>?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val toolchainService: JavaToolchainService?

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor?

    protected fun configureForkOptions(forkOptions: JavaForkOptions) {
        forkOptions.setMinHeapSize(this.minHeapSize.getOrNull())
        forkOptions.setMaxHeapSize(this.maxHeapSize.getOrNull())
        forkOptions.setExecutable(this.javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath())
        Companion.maybeAddOpensJvmArgs(this.javaLauncher.get(), forkOptions)
    }

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>?

    @get:Input
    @get:Optional
    abstract val minHeapSize: Property<String>?

    @get:Input
    @get:Optional
    abstract val maxHeapSize: Property<String>?

    companion object {
        private const val OPEN_MODULES_ARG = "java.prefs/java.util.prefs=ALL-UNNAMED"

        private fun maybeAddOpensJvmArgs(javaLauncher: JavaLauncher, forkOptions: JavaForkOptions) {
            if (toVersion(javaLauncher.metadata.javaRuntimeVersion)!!.isJava9Compatible) {
                forkOptions.jvmArgs("--add-opens", OPEN_MODULES_ARG)
            }
        }
    }
}
