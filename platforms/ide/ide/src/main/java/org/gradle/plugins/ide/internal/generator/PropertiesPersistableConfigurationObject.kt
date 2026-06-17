/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal.generator

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.internal.PropertiesTransformer
import org.gradle.util.internal.ConfigureUtil
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

abstract class PropertiesPersistableConfigurationObject protected constructor(private val transformer: PropertiesTransformer) : AbstractPersistableConfigurationObject() {
    private var properties: Properties? = null

    @Throws(Exception::class)
    override fun load(inputStream: InputStream?) {
        properties = Properties()
        properties!!.load(inputStream)
        load(properties)
    }

    override fun store(outputStream: OutputStream) {
        store(properties)
        transformer.transform(properties!!, outputStream)
    }

    protected abstract fun store(properties: Properties?)

    protected abstract fun load(properties: Properties?)

    fun transformAction(@DelegatesTo(Properties::class) action: Closure<*>?) {
        transformAction(ConfigureUtil.configureUsing<Properties?>(action))
    }

    /**
     * @param action transform action
     * @since 3.5
     */
    fun transformAction(action: Action<in Properties?>) {
        transformer.addAction(action)
    }
}
