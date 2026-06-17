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
package org.gradle.internal

class Pair<L, R> private constructor(@JvmField val left: L?, @JvmField val right: R?) {
    fun left(): L? {
        return left
    }

    fun right(): R? {
        return right
    }

    fun <T> pushLeft(t: T?): Pair<T?, Pair<L, R>?> {
        return Companion.of<T?, Pair<L, R>?>(t, this)
    }

    fun <T> pushRight(t: T?): Pair<Pair<L, R>?, T?> {
        return Companion.of<Pair<L, R>?, T?>(this, t)
    }

    fun <T> nestLeft(t: T?): Pair<Pair<T?, L?>?, R?> {
        return of<Pair<T?, L?>?, R?>(of<T?, L?>(t, left), right)
    }

    fun <T> nestRight(t: T?): Pair<L?, Pair<T?, R?>?> {
        return of<L?, Pair<T?, R?>?>(left, of<T?, R?>(t, right))
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val pair = o as Pair<*, *>

        return !(if (left != null) (left != pair.left) else pair.left != null) && !(if (right != null) (right != pair.right) else pair.right != null)
    }

    override fun hashCode(): Int {
        var result = if (left != null) left.hashCode() else 0
        result = 31 * result + (if (right != null) right.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return "Pair[" + left + "," + right + ']'
    }

    companion object {
        fun <L, R> of(left: L?, right: R?): Pair<L?, R?> {
            return Pair<L?, R?>(left, right)
        }
    }
}
