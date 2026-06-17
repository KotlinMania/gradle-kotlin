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
package org.gradle.api.internal.tasks.compile

import org.gradle.internal.concurrent.CompositeStoppable
import java.io.Closeable
import java.util.Locale
import javax.annotation.processing.Processor
import javax.tools.JavaCompiler

/**
 * Cleans up resources (e.g. file handles) after compilation has finished.
 */
internal class ResourceCleaningCompilationTask(private val delegate: JavaCompiler.CompilationTask, private val fileManager: Closeable) : JavaCompiler.CompilationTask {
    override fun addModules(moduleNames: Iterable<String?>?) {
    }

    override fun setProcessors(processors: Iterable<out Processor?>?) {
        delegate.setProcessors(processors)
    }

    override fun setLocale(locale: Locale?) {
        delegate.setLocale(locale)
    }

    override fun call(): Boolean? {
        try {
            return delegate.call()
        } finally {
            CompositeStoppable.stoppable(fileManager).stop()
            cleanupZipCache()
        }
    }

    /**
     * The javac file manager uses a shared ZIP cache which keeps file handles open
     * after compilation. It's supposed to be tunable with the -XDuseOptimizedZip parameter,
     * but the [JavaCompiler.getStandardFileManager]
     * method does not take arguments, so the cache can't be turned off.
     * So instead we clean it ourselves using reflection.
     */
    private fun cleanupZipCache() {
        try {
            val zipFileIndexCache = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache")
            val instance = zipFileIndexCache.getMethod("getSharedInstance").invoke(null)
            zipFileIndexCache.getMethod("clearCache").invoke(instance)
        } catch (e: Throwable) {
            // Not an OpenJDK-compatible compiler or signature changed
        }
    }
}
