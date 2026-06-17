/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.processing

import java.io.IOException
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.tools.FileObject
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject

/**
 * A decorator for the [Filer] which ensures that incremental
 * annotation processors don't break the incremental processing contract.
 */
class IncrementalFiler internal constructor(private val delegate: Filer, strategy: IncrementalProcessingStrategy) : Filer {
    private val strategy: IncrementalProcessingStrategy

    init {
        if (strategy == null) {
            throw NullPointerException("strategy")
        }
        this.strategy = strategy
    }

    @Throws(IOException::class)
    override fun createSourceFile(name: CharSequence?, vararg originatingElements: Element?): JavaFileObject? {
        strategy.recordGeneratedType(name, originatingElements)
        return delegate.createSourceFile(name, *originatingElements)
    }

    @Throws(IOException::class)
    override fun createClassFile(name: CharSequence?, vararg originatingElements: Element?): JavaFileObject? {
        strategy.recordGeneratedType(name, originatingElements)
        return delegate.createClassFile(name, *originatingElements)
    }

    @Throws(IOException::class)
    override fun createResource(location: JavaFileManager.Location?, pkg: CharSequence?, relativeName: CharSequence?, vararg originatingElements: Element?): FileObject? {
        // Prefer having javac validate the location over us, by calling it first.
        val resource = delegate.createResource(location, pkg, relativeName, *originatingElements)
        strategy.recordGeneratedResource(location, pkg, relativeName, originatingElements)
        return resource
    }

    @Throws(IOException::class)
    override fun getResource(location: JavaFileManager.Location?, pkg: CharSequence?, relativeName: CharSequence?): FileObject? {
        strategy.recordAccessedResource(location, pkg, relativeName)
        return delegate.getResource(location, pkg, relativeName)
    }
}
