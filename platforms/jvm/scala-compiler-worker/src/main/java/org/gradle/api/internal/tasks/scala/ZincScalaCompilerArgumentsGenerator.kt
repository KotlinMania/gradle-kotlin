/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

class ZincScalaCompilerArgumentsGenerator {
    fun generate(spec: ScalaCompileSpec): MutableList<String?> {
        val result: MutableList<String?> = ArrayList<String?>()

        val options = spec.getScalaCompileOptions()
        addFlag("-deprecation", options.isDeprecation(), result)
        addFlag("-unchecked", options.isUnchecked(), result)
        addConcatenatedOption("-g:", options.getDebugLevel(), result)
        addFlag("-optimise", options.isOptimize(), result)
        addOption("-encoding", options.getEncoding(), result)
        addFlag("-verbose", "verbose" == options.getDebugLevel(), result)
        addFlag("-Ydebug", "debug" == options.getDebugLevel(), result)
        if (options.getLoggingPhases() != null) {
            for (phase in options.getLoggingPhases()) {
                addConcatenatedOption("-Ylog:", phase, result)
            }
        }
        if (options.getAdditionalParameters() != null) {
            result.addAll(options.getAdditionalParameters()!!)
        }
        if (spec.getScalaCompilerPlugins() != null) {
            for (plugin in spec.getScalaCompilerPlugins()) {
                result.add("-Xplugin:" + plugin.getPath())
            }
        }

        return result
    }

    private fun addFlag(name: String?, value: Boolean, result: MutableList<String?>) {
        if (value) {
            result.add(name)
        }
    }

    private fun addOption(name: String?, value: Any?, result: MutableList<String?>) {
        if (value != null) {
            result.add(name)
            result.add(value.toString())
        }
    }

    private fun addConcatenatedOption(name: String?, value: Any?, result: MutableList<String?>) {
        if (value != null) {
            result.add(name + value.toString())
        }
    }
}
