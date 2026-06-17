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
package org.gradle.internal.serialize.bench

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.Arrays
import java.util.Random

/**
 * Benchmarks comparing two approaches for sorting map keys / set elements
 * before serialization:
 *
 *
 *  * **array** (current): `keySet().toArray(new Comparable[0])` + `Arrays.sort()`
 *  * **list** (reviewer suggestion): `new ArrayList<>(keySet())` + `list.sort(null)`
 *
 *
 * @see [PR review comment](https://github.com/gradle/gradle/pull/37290/files.r2996401630)
 */
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@State(Scope.Benchmark)
class SortedCollectionBenchmark {
    /**
     * Size parameters chosen to cover realistic instrumentation analysis scenarios:
     * small (8), medium (64), large (512).
     */
    @Param("8", "64")
    var size: Int = 0

    private var map: MutableMap<String?, MutableSet<String?>?>? = null
    private var set: MutableSet<String?>? = null

    @Setup(Level.Iteration)
    fun setup() {
        val rng = Random(42)
        map = HashMap<String?, MutableSet<String?>?>(size)
        set = HashSet<String?>(size)
        for (i in 0..<size) {
            // Simulate class-name-like keys (the real-world usage)
            val key = "org/gradle/internal/Class" + rng.nextInt(100000)
            set!!.add(key)

            val values: MutableSet<String?> = HashSet<String?>()
            val valueCount = 1 + rng.nextInt(5)
            for (j in 0..<valueCount) {
                values.add("org/gradle/api/Type" + rng.nextInt(100000))
            }
            map!!.put(key, values)
        }
    }

    // ---- Map key sorting ----
    @Benchmark
    fun mapKeys_arraySorted(bh: Blackhole) {
        // Mirrors the production code in SortedMapSerializer.write():
        // K[] sortedKeys = (K[]) value.keySet().toArray(new Comparable[0]);
        // The unchecked cast to K[] is erased at runtime, so the actual
        // array type is Comparable[].
        val sortedKeys = map!!.keys.toTypedArray<Comparable<*>?>()
        Arrays.sort(sortedKeys)
        for (key in sortedKeys) {
            bh.consume(key)
            bh.consume(map!!.get(key))
        }
    }

    @Benchmark
    fun mapKeys_listSorted(bh: Blackhole) {
        val sortedKeys: MutableList<String?> = ArrayList<String?>(map!!.keys)
        sortedKeys.sort(null)
        for (key in sortedKeys) {
            bh.consume(key)
            bh.consume(map!!.get(key))
        }
    }

    // ---- Set element sorting ----
    @Benchmark
    fun setElements_arraySorted(bh: Blackhole) {
        val sorted = set!!.toTypedArray<Comparable<*>?>()
        Arrays.sort(sorted)
        for (elem in sorted) {
            bh.consume(elem)
        }
    }

    @Benchmark
    fun setElements_listSorted(bh: Blackhole) {
        val sorted: MutableList<String?> = ArrayList<String?>(set)
        sorted.sort(null)
        for (elem in sorted) {
            bh.consume(elem)
        }
    }
}
