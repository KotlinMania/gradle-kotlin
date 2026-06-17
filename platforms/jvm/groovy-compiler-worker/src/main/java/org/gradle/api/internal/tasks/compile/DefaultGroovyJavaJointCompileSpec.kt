/*
 * Copyright 2012 the original author or authors.
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

import java.io.File

open class DefaultGroovyJavaJointCompileSpec : DefaultJavaCompileSpec(), GroovyJavaJointCompileSpec {
    private var groovyCompileOptions: MinimalGroovyCompileOptions? = null
    private var groovyClasspath: MutableList<File?>? = null

    override fun getGroovyCompileOptions(): MinimalGroovyCompileOptions? {
        return groovyCompileOptions
    }

    fun setGroovyCompileOptions(groovyCompileOptions: MinimalGroovyCompileOptions?) {
        this.groovyCompileOptions = groovyCompileOptions
    }

    override fun getGroovyClasspath(): MutableList<File?>? {
        return groovyClasspath
    }

    override fun setGroovyClasspath(groovyClasspath: MutableList<File?>?) {
        this.groovyClasspath = groovyClasspath
    }

    override fun incrementalCompilationEnabled(): Boolean {
        return getCompileOptions().previousCompilationDataFile != null
    }
}
