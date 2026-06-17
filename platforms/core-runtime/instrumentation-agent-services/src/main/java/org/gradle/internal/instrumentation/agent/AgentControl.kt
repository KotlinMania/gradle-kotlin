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
package org.gradle.internal.instrumentation.agent

import org.gradle.internal.UncheckedException
import org.slf4j.LoggerFactory
import java.lang.instrument.ClassFileTransformer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Provides methods to interact with the Java agent shipped with Gradle. Because of the different class loaders, it is hard to query the Agent class directly.
 *
 *
 * The agent class must follow a special protocol to be recognized properly: the class should have a method:
 * <pre>
 * public static boolean isApplied()
</pre> *
 * that returns if the agent is applied, i.e. if one of its `premain` or `agentmain` entry methods was called.
 *
 *
 * It is possible to have an agent in the classpath without actually applying it, so checking for the availability of the class is not enough.
 */
internal object AgentControl {
    private const val INSTRUMENTATION_AGENT_CLASS_NAME = "org.gradle.instrumentation.agent.Agent"

    val isInstrumentationAgentApplied: Boolean
        /**
         * Checks if the instrumentation agent class is applied to the current JVM.
         *
         * @return `true` if the agent was applied
         */
        get() {
            val isAppliedMethod =
                findAgentMethod(INSTRUMENTATION_AGENT_CLASS_NAME, "isApplied")
            if (isAppliedMethod == null) {
                return false
            }
            return callStaticAgentMethod(isAppliedMethod)
        }

    fun installTransformer(transformer: ClassFileTransformer): Boolean {
        val installTransformer = findAgentMethod(INSTRUMENTATION_AGENT_CLASS_NAME, "installTransformer", ClassFileTransformer::class.java)
        if (installTransformer == null) {
            return false
        }
        return callStaticAgentMethod(installTransformer, transformer)
    }

    private fun tryLoadAgentClass(agentClassName: String): Class<*>? {
        try {
            return ClassLoader.getSystemClassLoader().loadClass(agentClassName)
        } catch (e: ClassNotFoundException) {
            // This typically means that the agent is not loaded at all.
            // For now, this happens when running in a no-daemon mode, or when the Gradle distribution is not available.
            LoggerFactory.getLogger(AgentControl::class.java).debug("Agent {} is not loaded", agentClassName)
        }
        return null
    }

    private fun findAgentMethod(agentClassName: String, methodName: String, vararg args: Class<*>): Method? {
        val agentClass = tryLoadAgentClass(agentClassName)
        if (agentClass == null) {
            return null
        }
        try {
            val method = agentClass.getMethod(methodName, *args)
            method.setAccessible(true)
            return method
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(e)
        }
    }

    private fun callStaticAgentMethod(method: Method, vararg args: Any): Boolean {
        try {
            return method.invoke(null, *args) as Boolean
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException(e)
        } catch (e: InvocationTargetException) {
            throw UncheckedException.unwrapAndRethrow(e)
        }
    }
}
