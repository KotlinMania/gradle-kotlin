/*
 * Copyright 2020 the original author or authors.
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

import sbt.io.IO
import scala.Function0
import scala.collection.immutable.List
import scala.jdk.javaapi.CollectionConverters
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.util.Objects
import java.util.concurrent.Callable
import java.util.stream.Collectors

/**
 * This class implements AbstractClassLoaderCache in a way that allows safe
 * resource release when entries are evicted.
 *
 * Cache is based on file timestamps, because this method is used in sbt implementation.
 */
internal class TimeCheckingClassLoaderCache(maxSize: Int) : AbstractClassLoaderCache {
    private val commonParent: URLClassLoader
    private val cache: GuavaBackedClassLoaderCache<MutableSet<TimestampedFile?>?>

    internal class TimestampedFile(private val file: File?) {
        private val timestamp: Long

        init {
            this.timestamp = IO.getModifiedTimeOrZero(file)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as TimestampedFile
            return timestamp == that.timestamp &&
                    file == that.file
        }

        override fun hashCode(): Int {
            return Objects.hash(file, timestamp)
        }
    }

    init {
        commonParent = URLClassLoader(arrayOfNulls<URL>(0))
        cache = GuavaBackedClassLoaderCache<MutableSet<TimestampedFile?>?>(maxSize)
    }

    override fun commonParent(): ClassLoader {
        return commonParent
    }

    override fun apply(files: List<File?>?): ClassLoader? {
        try {
            val jFiles = CollectionConverters.asJava<File?>(files)
            return cache.get(getTimestampedFiles(jFiles), Callable {
                val urls = ArrayList<URL?>(jFiles.size)
                for (f in jFiles) {
                    urls.add(f.toURI().toURL())
                }
                URLClassLoader(urls.toTypedArray<URL?>(), commonParent)
            })
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun cachedCustomClassloader(files: List<File?>?, mkLoader: Function0<ClassLoader?>): ClassLoader? {
        try {
            return cache.get(getTimestampedFiles(CollectionConverters.asJava<File?>(files)), Callable { mkLoader.apply() })
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun getTimestampedFiles(fs: MutableList<File>): MutableSet<TimestampedFile?> {
        return fs.stream().map<TimestampedFile?> { file: File? -> TimestampedFile(file) }.collect(Collectors.toSet())
    }

    @Throws(IOException::class)
    override fun close() {
        cache.clear()
        commonParent.close()
    }
}
