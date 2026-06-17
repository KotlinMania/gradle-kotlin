/*
 * Copyright 2017 the original author or authors.
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

import com.dd.plist.NSObject
import com.dd.plist.PropertyListParser
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.internal.PropertyListTransformer
import org.gradle.util.internal.ConfigureUtil
import java.io.InputStream
import java.io.OutputStream

abstract class PropertyListPersistableConfigurationObject<T : NSObject?> protected constructor(private val clazz: Class<T?>, private val transformer: PropertyListTransformer<T?>) :
    AbstractPersistableConfigurationObject() {
    private var rootObject: T? = null

    protected abstract fun newRootObject(): T?

    @Throws(Exception::class)
    public override fun load(inputStream: InputStream) {
        rootObject = clazz.cast(PropertyListParser.parse(inputStream))
        if (rootObject == null) {
            rootObject = newRootObject()
        }
        load(rootObject)
    }

    public override fun store(outputStream: OutputStream?) {
        store(rootObject)
        transformer.transform(rootObject, outputStream!!)
    }

    protected abstract fun store(rootObject: T?)

    protected abstract fun load(rootObject: T?)

    fun transformAction(@DelegatesTo(NSObject::class) action: Closure<*>?) {
        transformAction(ConfigureUtil.configureUsing<T?>(action))
    }

    fun transformAction(action: Action<in T?>?) {
        transformer.addAction(action!!)
    }
}
