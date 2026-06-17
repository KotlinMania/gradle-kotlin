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
package org.gradle.api.internal.artifacts.repositories

import org.apache.ivy.util.StringUtils

/**
 * Utility methods originally used directly from [IvyPatternHelper], but
 * which have been now copied to facilitate alterations and also to isolate
 * the usages from Ivy concepts.
 */
object PatternHelper {
    const val TYPE_KEY: String = "type"

    const val EXT_KEY: String = "ext"

    const val ARTIFACT_KEY: String = "artifact"

    const val REVISION_KEY: String = "revision"

    const val MODULE_KEY: String = "module"

    const val ORGANISATION_KEY: String = "organisation"

    const val ORGANISATION_KEY2: String = "organization"

    const val ORGANISATION_PATH_KEY: String = "orgPath"

    /**
     * Selective copy of [IvyPatternHelper.substituteTokens],
     * necessary because we allow for paths which are no longer supported by the Ivy code (for
     * example paths with parent traversals, i.e. with ".." in them).
     */
    fun substituteTokens(pattern: String, attributes: MutableMap<String, String>): String {
        val tokens: MutableMap<String, Any> = HashMap<String, Any>(attributes)
        if (tokens.containsKey(ORGANISATION_KEY) && !tokens.containsKey(ORGANISATION_KEY2)) {
            tokens.put(ORGANISATION_KEY2, tokens.get(ORGANISATION_KEY)!!)
        }
        if (tokens.containsKey(ORGANISATION_KEY)
            && !tokens.containsKey(ORGANISATION_PATH_KEY)
        ) {
            val org = tokens.get(ORGANISATION_KEY) as String?
            tokens.put(org.gradle.api.internal.artifacts.repositories.PatternHelper.ORGANISATION_PATH_KEY, if (org == null) "" else org.replace('.', '/'))
        }

        val buffer = StringBuilder()

        var optionalPart: StringBuilder? = null
        var tokenBuffer: StringBuilder? = null
        var insideOptionalPart = false
        var insideToken = false
        var tokenSeen = false
        var tokenHadValue = false

        for (i in 0..<pattern.length) {
            val ch = pattern.get(i)
            when (ch) {
                '(' -> {
                    require(!insideOptionalPart) {
                        ("invalid start of optional part at position " + i + " in pattern "
                                + pattern)
                    }

                    optionalPart = StringBuilder()
                    insideOptionalPart = true
                    tokenSeen = false
                    tokenHadValue = false
                }

                ')' -> {
                    require(!(!insideOptionalPart || insideToken)) {
                        ("invalid end of optional part at position " + i + " in pattern "
                                + pattern)
                    }

                    if (tokenHadValue) {
                        buffer.append(optionalPart.toString())
                    } else if (!tokenSeen) {
                        buffer.append('(').append(optionalPart.toString()).append(')')
                    }
                    insideOptionalPart = false
                }

                '[' -> {
                    require(!insideToken) {
                        ("invalid start of token at position "
                                + i + " in pattern " + pattern)
                    }

                    tokenBuffer = StringBuilder()
                    insideToken = true
                }

                ']' -> {
                    require(insideToken) {
                        ("invalid end of token at position " + i
                                + " in pattern " + pattern)
                    }

                    val token = tokenBuffer.toString()
                    val tokenValue = tokens.get(token)
                    val value = if (tokenValue == null) null else tokenValue.toString()
                    if (insideOptionalPart) {
                        tokenHadValue = !StringUtils.isNullOrEmpty(value)
                        optionalPart!!.append(value)
                    } else {
                        if (value == null) { // the token wasn't set, it's kept as is
                            value = "[" + token + "]"
                        }
                        buffer.append(value)
                    }
                    insideToken = false
                    tokenSeen = true
                }

                else -> if (insideToken) {
                    tokenBuffer!!.append(ch)
                } else if (insideOptionalPart) {
                    optionalPart!!.append(ch)
                } else {
                    buffer.append(ch)
                }
            }
        }

        require(!insideToken) {
            ("last token hasn't been closed in pattern "
                    + pattern)
        }

        require(!insideOptionalPart) {
            ("optional part hasn't been closed in pattern "
                    + pattern)
        }

        return buffer.toString()
    }

    /**
     * Exact copy of [IvyPatternHelper.getTokenString], used to isolate
     * the usages from Ivy concepts.
     */
    fun getTokenString(token: String): String {
        return "[" + token + "]"
    }
}
