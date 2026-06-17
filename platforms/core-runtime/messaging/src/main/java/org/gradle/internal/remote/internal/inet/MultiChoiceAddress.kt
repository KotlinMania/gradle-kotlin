/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal.inet

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import java.net.InetAddress
import java.util.UUID

class MultiChoiceAddress(val canonicalAddress: UUID, private val port: Int, candidates: MutableList<InetAddress>) : InetEndpoint {
    private val candidates: MutableList<InetAddress>

    init {
        this.candidates = ArrayList<InetAddress>(candidates)
    }

    override fun getDisplayName(): String {
        return "[" + canonicalAddress + " port:" + port + ", addresses:" + candidates + "]"
    }

    override fun getCandidates(): MutableList<InetAddress> {
        return candidates
    }

    override fun getPort(): Int {
        return port
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun equals(o: Any): Boolean {
        if (o === this) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val other = o as MultiChoiceAddress
        return other.canonicalAddress == canonicalAddress && port == other.port && candidates == other.candidates
    }

    override fun hashCode(): Int {
        return canonicalAddress.hashCode()
    }

    fun addAddresses(candidates: Iterable<InetAddress>): MultiChoiceAddress {
        return MultiChoiceAddress(canonicalAddress, port, Lists.newArrayList<InetAddress>(Iterables.concat<InetAddress>(candidates, this.candidates)))
    }
}
