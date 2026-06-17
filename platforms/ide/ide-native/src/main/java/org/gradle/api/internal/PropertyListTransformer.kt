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
package org.gradle.api.internal

import com.dd.plist.NSObject
import com.dd.plist.XMLPropertyListWriter
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.MutableActionSet
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.IOException
import java.io.OutputStream

class PropertyListTransformer<T : NSObject?> : Transformer<T?, T?> {
    private val actions = MutableActionSet<T?>()

    /**
     * Adds an action to be executed when property lists are transformed.
     * @param action the action to add
     */
    fun addAction(action: Action<in T?>) {
        actions.add(action)
    }

    /**
     * Transforms a property list object. This will modify the
     * original.
     * @param original the property list to transform
     * @return the transformed property list
     */
    override fun transform(original: T?): T? {
        return doTransform(original)
    }

    /**
     * Transforms a property list object and write them out to a stream.
     * This will modify the original property list.
     * @param original the property list to transform
     * @param destination the stream to write the property list to
     */
    fun transform(original: T?, destination: OutputStream) {
        try {
            XMLPropertyListWriter.write(doTransform(original), destination)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    /**
     * Transforms a property list object.  This will modify the
     * original.
     * @param original the property list to transform
     * @return the transformed property list
     */
    private fun doTransform(original: T?): T? {
        actions.execute(original)
        return original
    }
}
