/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.instrumentation.agent

import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import kotlin.concurrent.Volatile

/**
 * An entry point for the on-the-fly bytecode instrumentation agent.
 */
object Agent {
    @Volatile
    private var instrumentation: Instrumentation? = null

    fun premain(agentArgs: String?, inst: Instrumentation?) {
        doMain(inst)
    }

    fun agentmain(agentArgs: String?, inst: Instrumentation?) {
        doMain(inst)
    }

    fun doMain(inst: Instrumentation?) {
        instrumentation = inst
    }

    @get:Suppress("unused")
    val isApplied: Boolean
        get() = instrumentation != null

    @Suppress("unused") // Used reflectively.
    fun installTransformer(transformer: ClassFileTransformer?): Boolean {
        val inst = instrumentation
        if (inst != null) {
            inst.addTransformer(transformer)
            return true
        }
        return false
    }
}
