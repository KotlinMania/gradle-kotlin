/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.util.internal

import groovy.lang.Closure
import groovy.lang.GroovyObject
import org.gradle.api.Action
import org.gradle.api.tasks.AntBuilderAware

/**
 * Externalised from PatternSet to isolate the Groovy usage.
 */
class PatternSetAntBuilderDelegate(private val includes: MutableSet<String>, private val excludes: MutableSet<String>, private val caseSensitive: Boolean) : AntBuilderAware {
    override fun addToAntBuilder(node: Any, childNodeName: String): Any {
        return and(node, object : Action<Any> {
            override fun execute(node: Any) {
                if (!includes.isEmpty()) {
                    or(node, object : Action<Any> {
                        override fun execute(node: Any) {
                            addFilenames(node, includes, caseSensitive)
                        }
                    })
                }

                if (!excludes.isEmpty()) {
                    not(node, object : Action<Any> {
                        override fun execute(node: Any) {
                            or(node, object : Action<Any> {
                                override fun execute(node: Any) {
                                    addFilenames(node, excludes, caseSensitive)
                                }
                            })
                        }
                    })
                }
            }
        })
    }

    companion object {
        private fun logical(node: Any, op: String, withNode: Action<Any>): Any {
            val groovyObject = node as GroovyObject
            groovyObject.invokeMethod(op, object : Closure<Any?>(null, null) {
                @Suppress("unused")
                fun doCall() {
                    withNode.execute(getDelegate())
                }
            })
            return node
        }

        @JvmStatic
        fun and(node: Any, withNode: Action<Any>): Any {
            return logical(node, "and", withNode)
        }

        private fun or(node: Any, withNode: Action<Any>): Any {
            return logical(node, "or", withNode)
        }

        private fun not(node: Any, withNode: Action<Any>): Any {
            return logical(node, "not", withNode)
        }

        private fun addFilenames(node: Any, filenames: Iterable<String>, caseSensitive: Boolean): Any {
            val groovyObject = node as GroovyObject
            val props: MutableMap<String, Any> = HashMap<String, Any>(2)
            props.put("casesensitive", caseSensitive)
            for (filename in filenames) {
                props.put("name", filename.replace("\\$".toRegex(), "\\$\\$"))
                groovyObject.invokeMethod("filename", props)
            }
            return node
        }
    }
}
