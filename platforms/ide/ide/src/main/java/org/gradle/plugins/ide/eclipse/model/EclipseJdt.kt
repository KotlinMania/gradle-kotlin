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
package org.gradle.plugins.ide.eclipse.model

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.plugins.ide.api.PropertiesFileContentMerger
import org.gradle.plugins.ide.internal.IdeDeprecations
import org.gradle.util.internal.ConfigureUtil
import javax.inject.Inject

/**
 * Enables fine-tuning jdt details of the Eclipse plugin
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'java'
 * id 'eclipse'
 * }
 *
 * eclipse {
 * jdt {
 * //if you want to alter the java versions (by default they are configured with gradle java plugin settings):
 * sourceCompatibility = 1.6
 * targetCompatibility = 1.5
 * javaRuntimeName = "J2SE-1.5"
 *
 * file {
 * //whenMerged closure is the highest voodoo
 * //and probably should be used only to solve tricky edge cases.
 * //the type passed to the closure is [Jdt]
 *
 * //closure executed after jdt file content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { jdt
 * //you can tinker with the [Jdt] here
 * }
 *
 * //withProperties allows addition of properties not currently
 * //modeled by Gradle
 * withProperties { properties -&gt;
 * //you can tinker with the [java.util.Properties] here
 * }
 * }
 * }
 * }
</pre> *
 */
abstract class EclipseJdt @Inject constructor(private val file: PropertiesFileContentMerger?) {
    private var sourceCompatibility = current()

    private var targetCompatibility = current()

    @get:Deprecated("Will be removed in Gradle 10.")
    @set:Deprecated("Will be removed in Gradle 10.")
    var javaRuntimeName: String? = null
        /**
         * The name of the Java Runtime to use.
         *
         *
         * For example see docs for [EclipseJdt]
         *
         */
        get() {
            IdeDeprecations.nagDeprecatedProperty(EclipseJdt::class.java, "javaRuntimeName")
            return field
        }
        /**
         * Set Java Runtime name.
         *
         */
        set(javaRuntimeName) {
            IdeDeprecations.nagDeprecatedProperty(EclipseJdt::class.java, "javaRuntimeName")
            field = javaRuntimeName
        }

    /**
     * The source Java language level.
     *
     *
     * For example see docs for [EclipseJdt]
     */
    fun getSourceCompatibility(): JavaVersion? {
        return sourceCompatibility
    }

    /**
     * Sets source compatibility.
     *
     * @since 4.0
     */
    fun setSourceCompatibility(sourceCompatibility: JavaVersion?) {
        setSourceCompatibility(sourceCompatibility as Any?)
    }

    fun setSourceCompatibility(sourceCompatibility: Any?) {
        val version = toVersion(sourceCompatibility)
        if (version != null) {
            this.sourceCompatibility = version
        }
    }

    /**
     * The target JVM to generate `.class` files for.
     *
     *
     * For example see docs for [EclipseJdt]
     */
    fun getTargetCompatibility(): JavaVersion? {
        return targetCompatibility
    }

    /**
     * Sets target compatibility.
     *
     * @since 4.0
     */
    fun setTargetCompatibility(targetCompatibility: JavaVersion?) {
        setTargetCompatibility(targetCompatibility as Any?)
    }

    fun setTargetCompatibility(targetCompatibility: Any?) {
        val version = toVersion(targetCompatibility)
        if (version != null) {
            this.targetCompatibility = version
        }
    }

    /**
     * See [.file]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun getFile(): PropertiesFileContentMerger? {
        IdeDeprecations.nagDeprecatedProperty(EclipseJdt::class.java, "file")
        return file
    }

    /**
     * Enables advanced configuration like affecting the way existing jdt file content
     * is merged with gradle build information
     *
     *
     * The object passed to whenMerged{} and beforeMerged{} closures is of type [Jdt]
     *
     *
     * The object passed to withProperties{} closures is of type [java.util.Properties]
     *
     *
     * For example see docs for [EclipseJdt]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun file(@DelegatesTo(PropertiesFileContentMerger::class) closure: Closure<*>?) {
        IdeDeprecations.nagDeprecatedProperty(EclipseJdt::class.java, "file")
        ConfigureUtil.configure<PropertiesFileContentMerger?>(closure, file)
    }

    /**
     * Enables advanced configuration like affecting the way existing jdt file content
     * is merged with gradle build information
     *
     *
     * The object passed to whenMerged{} and beforeMerged{} actions is of type [Jdt]
     *
     *
     * The object passed to withProperties{} actions is of type [java.util.Properties]
     *
     *
     * For example see docs for [EclipseJdt]
     *
     * @since 3.5
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun file(action: Action<in PropertiesFileContentMerger?>) {
        IdeDeprecations.nagDeprecatedProperty(EclipseJdt::class.java, "file")
        action.execute(file)
    }
}
