/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.api.Action
import org.gradle.internal.IoActions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.util.function.Function

/**
 * The peculiars of the swiftc incremental compiler can be extracted from the Driver's source code:
 * https://github.com/apple/swift/tree/d139ab29681d679337245f399dd8c76d620aa1aa/lib/Driver
 * And docs:
 * https://github.com/apple/swift/blob/d139ab29681d679337245f399dd8c76d620aa1aa/docs/Driver.md
 *
 * The incremental compiler uses the timestamp of source files and the timestamp in module.swiftdeps to
 * determine which files should be considered for compilation initially.  The compiler then looks at the
 * individual object's .swiftdeps file to build a dependency graph between changed and unchanged files.
 *
 * The incremental compiler will rebuild everything when:
 * - A source file is removed
 * - A different version of swiftc is used
 * - Different compiler arguments are used
 *
 * We work around issues with timestamps by changing module.swiftdeps and setting any changed files to
 * a timestamp of 0.  swiftc then sees those source files as different from the last compilation.
 *
 * If we have any issues reading or writing the swiftdeps file, we bail out and disable incremental compilation.
 */
internal class SwiftDepsHandler {
    @Throws(FileNotFoundException::class)
    fun parse(moduleSwiftDeps: File): SwiftDeps {
        return IoActions.withResource<FileInputStream?, SwiftDeps>(FileInputStream(moduleSwiftDeps), Function { fileInputStream: FileInputStream? ->
            val yaml = Yaml(Constructor(SwiftDeps::class.java, LoaderOptions()))
            yaml.loadAs<SwiftDeps?>(fileInputStream, SwiftDeps::class.java)
        })
    }

    private fun adjustTimestamps(swiftDeps: SwiftDeps, changedSources: MutableCollection<File>) {
        // Update any previously known files with a bogus timestamp to force a rebuild

        for (changedSource in changedSources) {
            if (swiftDeps.inputs!!.containsKey(changedSource.getAbsolutePath())) {
                swiftDeps.inputs!!.put(changedSource.getAbsolutePath(), RESET_TIMESTAMP)
            }
        }
    }

    private fun write(moduleSwiftDeps: File, swiftDeps: SwiftDeps) {
        IoActions.writeTextFile(moduleSwiftDeps, object : Action<BufferedWriter?> {
            override fun execute(bufferedWriter: BufferedWriter) {
                // Rewrite swiftc generated YAML file with our understanding of the current state of
                // swift sources. This doesn't use Yaml.dump because snakeyaml produces a YAML file
                // that swiftc cannot read.
                val pw = PrintWriter(bufferedWriter)
                pw.println("version: \"" + swiftDeps.version + "\"")
                pw.println("options: \"" + swiftDeps.options + "\"")
                pw.println("build_time: [" + swiftDeps.build_time!!.get(0) + ", " + swiftDeps.build_time!!.get(1) + "]")
                pw.println("inputs:")
                swiftDeps.inputs!!.forEach { (file: String?, timestamp: MutableList<*>?) ->
                    pw.println("  \"" + file + "\": [" + timestamp!!.get(0) + ", " + timestamp.get(1) + "]")
                }
            }
        })
    }

    fun adjustTimestampsFor(moduleSwiftDeps: File, changedSources: MutableCollection<File>): Boolean {
        if (moduleSwiftDeps.exists() && !changedSources.isEmpty()) {
            try {
                val swiftDeps = parse(moduleSwiftDeps)
                adjustTimestamps(swiftDeps, changedSources)
                write(moduleSwiftDeps, swiftDeps)
            } catch (e: Exception) {
                LOGGER.debug("could not update module.swiftdeps", e)
                return false
            }
        }
        return true
    }

    //CHECKSTYLE:OFF
    // This is used to parse a YAML file
    class SwiftDeps {
        var version: String? = null
        var options: String? = null
        private var build_time: MutableList<Long?>? = null
        private var inputs: MutableMap<String?, MutableList<*>?>? = null

        fun getBuild_time(): MutableList<Long?> {
            return build_time!!
        }

        fun setBuild_time(build_time: MutableList<Long?>) {
            this.build_time = build_time
        }

        fun getInputs(): MutableMap<String?, MutableList<*>?> {
            return inputs!!
        }

        fun setInputs(inputs: MutableMap<String?, MutableList<*>?>) {
            this.inputs = inputs
        }

        override fun toString(): String {
            return "SwiftDeps{" +
                    "version='" + version + '\'' +
                    ", options='" + options + '\'' +
                    ", build_time=" + build_time +
                    ", inputs=" + inputs +
                    '}'
        }
    } //CHECKSTYLE:ON

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SwiftDepsHandler::class.java)
        val RESET_TIMESTAMP: MutableList<*> = mutableListOf<Long?>(0L, 0L)
    }
}
