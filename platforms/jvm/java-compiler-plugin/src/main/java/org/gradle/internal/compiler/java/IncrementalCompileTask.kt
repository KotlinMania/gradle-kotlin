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
package org.gradle.internal.compiler.java

import com.sun.source.util.JavacTask
import org.gradle.internal.compiler.java.listeners.classnames.ClassNameCollector
import org.gradle.internal.compiler.java.listeners.constants.ConstantDependentsConsumer
import org.gradle.internal.compiler.java.listeners.constants.ConstantsCollector
import java.io.File
import java.util.Locale
import java.util.Optional
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import javax.annotation.processing.Processor
import javax.tools.JavaCompiler

/**
 * This is a Java compiler plugin, which must be loaded in the same classloader
 * as the one which loads the JDK compiler itself. For this reason this task lives
 * in its own subproject and uses as little dependencies as possible (in particular
 * it only depends on JDK types).
 *
 * It's accessed with reflection so move it with care to other packages.
 *
 * This class is therefore loaded (and tested) via reflection in org.gradle.api.internal.tasks.compile.JdkTools.
 */
@Suppress("unused")
class IncrementalCompileTask(
    delegate: JavaCompiler.CompilationTask,
    private val relativize: Function<File?, Optional<String?>?>?,
    private val classBackupService: Consumer<String?>?,
    private val classNameConsumer: Consumer<MutableMap<String?, MutableSet<String?>?>?>,
    publicDependentDelegate: BiConsumer<String?, String?>?,
    privateDependentDelegate: BiConsumer<String?, String?>?
) : JavaCompiler.CompilationTask {
    private val constantDependentsConsumer: ConstantDependentsConsumer
    private val delegate: JavacTask

    init {
        this.constantDependentsConsumer = ConstantDependentsConsumer(publicDependentDelegate, privateDependentDelegate)
        if (delegate is JavacTask) {
            this.delegate = delegate
        } else {
            throw UnsupportedOperationException("Unexpected Java compile task: " + delegate.javaClass.getName())
        }
    }

    override fun addModules(moduleNames: Iterable<String?>?) {
        delegate.addModules(moduleNames)
    }

    override fun setProcessors(processors: Iterable<out Processor?>?) {
        delegate.setProcessors(processors)
    }

    override fun setLocale(locale: Locale?) {
        delegate.setLocale(locale)
    }

    override fun call(): Boolean? {
        val classNameCollector = ClassNameCollector(relativize, classBackupService, delegate.getElements())
        val constantsCollector = ConstantsCollector(delegate, constantDependentsConsumer)
        delegate.addTaskListener(classNameCollector)
        delegate.addTaskListener(constantsCollector)
        try {
            return delegate.call()
        } finally {
            classNameConsumer.accept(classNameCollector.getMapping())
        }
    }
}
