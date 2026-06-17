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

import java.util.Locale
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * A decorator for the [ProcessingEnvironment] provided by the Java compiler,
 * which allows us to intercept calls from annotation processors in order to validate
 * their behavior.
 */
internal class IncrementalProcessingEnvironment(private val delegate: ProcessingEnvironment, private val filer: IncrementalFiler?) : ProcessingEnvironment {
    override fun getOptions(): MutableMap<String?, String?>? {
        return delegate.getOptions()
    }

    override fun getMessager(): Messager? {
        return delegate.getMessager()
    }

    override fun getFiler(): Filer? {
        return filer
    }

    override fun getElementUtils(): Elements? {
        return delegate.getElementUtils()
    }

    override fun getTypeUtils(): Types? {
        return delegate.getTypeUtils()
    }

    override fun getSourceVersion(): SourceVersion? {
        return delegate.getSourceVersion()
    }

    override fun getLocale(): Locale? {
        return delegate.getLocale()
    }
}
