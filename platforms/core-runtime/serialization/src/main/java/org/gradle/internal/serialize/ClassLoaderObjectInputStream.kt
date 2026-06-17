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
package org.gradle.internal.serialize

import org.gradle.api.JavaVersion
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

open class ClassLoaderObjectInputStream(`in`: InputStream?, val classLoader: ClassLoader) : ObjectInputStream(`in`) {
    @Throws(IOException::class, ClassNotFoundException::class)
    override fun resolveClass(desc: ObjectStreamClass): Class<*>? {
        try {
            return Class.forName(desc.getName(), false, this.classLoader)
        } catch (e: ClassNotFoundException) {
            return super.resolveClass(desc)
        } catch (e: UnsupportedClassVersionError) {
            try {
                val majorVersion = JavaClassUtil.getClassMajorVersion(desc.getName(), this.classLoader)
                if (majorVersion != null) {
                    throw UnsupportedClassVersionErrorWithJavaVersion(e, JavaVersion.forClassVersion(majorVersion))
                }
                // We could not find the class. Throw the original error.
                throw e
            } catch (ignored: IOException) {
                // There was an error parsing the class. Throw the original error.
                throw e
            }
        }
    }

    /**
     * Specialization of [UnsupportedClassVersionError] which includes the [JavaVersion] of
     * the class which is unsupported. The base class only includes the class version in the error message
     * and does not provide programmatic access.
     */
    class UnsupportedClassVersionErrorWithJavaVersion(cause: UnsupportedClassVersionError, version: JavaVersion?) : UnsupportedClassVersionError(cause.message) {
        @JvmField
        val version: JavaVersion?

        init {
            initCause(cause)
            this.version = version
        }
    }
}
