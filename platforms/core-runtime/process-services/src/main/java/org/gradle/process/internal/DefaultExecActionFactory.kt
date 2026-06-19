/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.process.internal

import com.google.common.collect.ImmutableMap
import org.gradle.api.file.FileCollection
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.util.MergeOptionsUtil
import org.jspecify.annotations.NullMarked
import java.io.File

/**
 * Represents effective options for forking a Java process.
 * It intentionally does not expose JvmOptions directly, as JvmOptions is not immutable yet.
 *
 * Strongly relates to [JavaForkOptions].
 */
@NullMarked
class EffectiveJavaForkOptions(@JvmField val executable: String, @JvmField val workingDir: File, environment: MutableMap<String, Any>, jvmOptions: JvmOptions) {
    @JvmField
    val environment: MutableMap<String, Any>
    @JvmField
    val jvmOptions: ReadOnlyJvmOptions

    init {
        this.environment = ImmutableMap.copyOf<String, Any>(environment)
        this.jvmOptions = ReadOnlyJvmOptions(jvmOptions)
    }

    /**
     * Returns true if the given options are compatible with this set of options.
     */
    fun isCompatibleWith(forkOptions: EffectiveJavaForkOptions): Boolean {
        return jvmOptions.debug == forkOptions.jvmOptions.debug && jvmOptions.enableAssertions == forkOptions.jvmOptions.enableAssertions && MergeOptionsUtil.normalized(executable) == MergeOptionsUtil.normalized(
            forkOptions.executable
        )
                && workingDir == forkOptions.workingDir
                && MergeOptionsUtil.normalized(jvmOptions.defaultCharacterEncoding) == MergeOptionsUtil.normalized(forkOptions.jvmOptions.defaultCharacterEncoding)
                && MergeOptionsUtil.getHeapSizeMb(jvmOptions.minHeapSize) >= MergeOptionsUtil.getHeapSizeMb(forkOptions.jvmOptions.minHeapSize) && MergeOptionsUtil.getHeapSizeMb(
            jvmOptions.maxHeapSize
        ) >= MergeOptionsUtil.getHeapSizeMb(forkOptions.jvmOptions.maxHeapSize) && MergeOptionsUtil.normalized(jvmOptions.jvmArgs).containsAll(
            MergeOptionsUtil.normalized(
                forkOptions.jvmOptions.jvmArgs
            )
        )
                && MergeOptionsUtil.containsAll(jvmOptions.mutableSystemProperties as MutableMap<String?, Any?>, forkOptions.jvmOptions.mutableSystemProperties as MutableMap<String?, Any?>)
                && MergeOptionsUtil.containsAll(environment as MutableMap<String?, Any?>, forkOptions.environment as MutableMap<String?, Any?>)
                && jvmOptions.bootstrapClasspath.getFiles().containsAll(forkOptions.jvmOptions.bootstrapClasspath.getFiles())
    }

    fun copyTo(target: JavaExecHandleBuilder) {
        target.setExecutable(executable)
        target.setWorkingDir(workingDir)
        target.setEnvironment(environment)
        target.copyJavaForkOptions(jvmOptions)
    }

    override fun toString(): String {
        return "EffectiveJavaForkOptions{" +
                "executable='" + executable + '\'' +
                ", workingDir=" + workingDir +
                ", environment=" + environment +
                ", jvmOptions=" + jvmOptions +
                '}'
    }

    class ReadOnlyJvmOptions(private val delegate: JvmOptions) {
        val minHeapSize: String
            get() = delegate.minHeapSize!!

        val maxHeapSize: String
            get() = delegate.maxHeapSize!!

        val debug: Boolean
            get() = delegate.debug

        val enableAssertions: Boolean
            get() = delegate.enableAssertions

        val defaultCharacterEncoding: String
            get() = delegate.defaultCharacterEncoding!!

        val bootstrapClasspath: FileCollection
            get() = delegate.getBootstrapClasspath()

        val jvmArgs: MutableList<String>
            get() = delegate.jvmArgs.filterNotNull().toMutableList()

        val allJvmArgs: MutableList<String>
            get() = delegate.allJvmArgs.filterNotNull().toMutableList()

        val mutableSystemProperties: MutableMap<String, Any>
            get() = delegate.mutableSystemProperties.entries
                .associate { it.key!! to it.value as Any }
                .toMutableMap()

        fun copyTo(target: JavaForkOptions) {
            this.delegate.copyTo(target)
        }
    }
}
