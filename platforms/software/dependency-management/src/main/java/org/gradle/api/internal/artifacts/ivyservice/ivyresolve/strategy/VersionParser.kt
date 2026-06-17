/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import com.google.common.primitives.Longs
import org.gradle.api.Transformer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(Scope.Global::class)
class VersionParser : Transformer<Version?, String?> {
    private val cache: MutableMap<String?, Version?> = ConcurrentHashMap<String?, Version?>()

    override fun transform(original: String?): Version? {
        return cache.computeIfAbsent(original) { original: String? -> Companion.parse(original!!) }
    }

    private class DefaultVersion(private val source: String, parts: MutableList<String?>, baseVersion: DefaultVersion?) : Version {
        private val parts: Array<String?>
        private val numericParts: Array<Long?>
        private val baseVersion: DefaultVersion

        init {
            this.parts = parts.toTypedArray<String?>()
            this.numericParts = arrayOfNulls<Long>(this.parts.size)
            for (i in parts.indices) {
                this.numericParts[i] = Longs.tryParse(this.parts[i]!!)
            }
            this.baseVersion = if (baseVersion == null) this else baseVersion
        }

        override fun toString(): String {
            return source
        }

        override fun equals(obj: Any?): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as DefaultVersion
            return source == other.source
        }

        override fun hashCode(): Int {
            return source.hashCode()
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        override fun isQualified(): Boolean {
            return baseVersion !== this
        }

        override fun getBaseVersion(): Version {
            return baseVersion
        }

        override fun getParts(): Array<String?> {
            return parts
        }

        override fun getNumericParts(): Array<Long?> {
            return numericParts
        }

        override fun getSource(): String {
            return source
        }
    }

    companion object {
        private fun parse(original: String): Version {
            val parts: MutableList<String?> = ArrayList<String?>()
            var digit = false
            var startPart = 0
            var pos = 0
            var endBase = 0
            var endBaseStr = 0
            while (pos < original.length) {
                val ch = original.get(pos)
                if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                    parts.add(original.substring(startPart, pos))
                    startPart = pos + 1
                    digit = false
                    if (ch != '.' && endBaseStr == 0) {
                        endBase = parts.size
                        endBaseStr = pos
                    }
                } else if (ch >= '0' && ch <= '9') {
                    if (!digit && pos > startPart) {
                        if (endBaseStr == 0) {
                            endBase = parts.size + 1
                            endBaseStr = pos
                        }
                        parts.add(original.substring(startPart, pos))
                        startPart = pos
                    }
                    digit = true
                } else {
                    if (digit) {
                        if (endBaseStr == 0) {
                            endBase = parts.size + 1
                            endBaseStr = pos
                        }
                        parts.add(original.substring(startPart, pos))
                        startPart = pos
                    }
                    digit = false
                }
                pos++
            }
            if (pos > startPart) {
                parts.add(original.substring(startPart, pos))
            }
            var base: DefaultVersion? = null
            if (endBaseStr > 0) {
                base = DefaultVersion(original.substring(0, endBaseStr), parts.subList(0, endBase), null)
            }
            return DefaultVersion(original, parts, base)
        }
    }
}
