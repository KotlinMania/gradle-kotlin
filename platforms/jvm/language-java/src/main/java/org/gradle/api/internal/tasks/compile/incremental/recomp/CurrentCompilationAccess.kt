/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.gradle.api.Action
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.time.Time.startTimer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Objects
import java.util.stream.Collectors

class CurrentCompilationAccess(private val classSetAnalyzer: ClassSetAnalyzer, private val buildOperationExecutor: BuildOperationExecutor) {
    private var classpathSnapshot: ClassSetAnalysisData? = null

    fun analyzeOutputFolder(outputFolder: File?): ClassSetAnalysisData? {
        val clock = startTimer()
        val snapshot = classSetAnalyzer.analyzeOutputFolder(outputFolder)
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.elapsed)
        return snapshot
    }


    fun getClasspathSnapshot(entries: Iterable<File>): ClassSetAnalysisData? {
        if (classpathSnapshot == null) {
            val clock = startTimer()
            classpathSnapshot = ClassSetAnalysisData.Companion.merge(doSnapshot(entries))
            LOG.info("Created classpath snapshot for incremental compilation in {}.", clock.elapsed)
        }
        return classpathSnapshot
    }

    private fun doSnapshot(entries: Iterable<File>): MutableList<ClassSetAnalysisData?> {
        return snapshotAll(entries).stream()
            .map<ClassSetAnalysisData?> { obj: CreateSnapshot? -> obj!!.snapshot }
            .filter { obj: ClassSetAnalysisData? -> Objects.nonNull(obj) }
            .collect(Collectors.toList())
    }

    private fun snapshotAll(entries: Iterable<File>): MutableList<CreateSnapshot?> {
        val snapshotOperations: MutableList<CreateSnapshot?> = ArrayList<CreateSnapshot?>()

        buildOperationExecutor.runAll<CreateSnapshot?>(Action { buildOperationQueue: BuildOperationQueue<CreateSnapshot?>? ->
            for (entry in entries) {
                val operation: CreateSnapshot = CurrentCompilationAccess.CreateSnapshot(entry)
                snapshotOperations.add(operation)
                buildOperationQueue!!.add(operation)
            }
        })
        return snapshotOperations
    }

    private inner class CreateSnapshot(private val entry: File) : RunnableBuildOperation {
        var snapshot: ClassSetAnalysisData? = null
            private set

        override fun run(context: BuildOperationContext) {
            if (entry.exists()) {
                snapshot = classSetAnalyzer.analyzeClasspathEntry(entry)
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor.displayName("Create incremental compile snapshot for " + entry)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CurrentCompilationAccess::class.java)
    }
}
