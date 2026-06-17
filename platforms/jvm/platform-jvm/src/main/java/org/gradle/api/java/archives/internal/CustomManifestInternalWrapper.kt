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
package org.gradle.api.java.archives.internal

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.provider.Provider
import java.io.OutputStream

/**
 * A wrapper around a [Manifest] that also implements [ManifestInternal], delegating all Manifest methods to the wrapped Manifest
 * and implementing the [ManifestInternal.writeTo] method using the supplied content charset.  This should only be used to wrap a Manifest
 * that does not already implement [ManifestInternal].
 */
class CustomManifestInternalWrapper(private val delegate: Manifest, private val contentCharset: Provider<String?>) : ManifestInternal {
    override fun writeTo(outputStream: OutputStream?): Manifest {
        ManifestInternal.ManifestWriter.writeTo(this, outputStream, contentCharset.get())
        return this
    }

    override fun getAttributes(): Attributes? {
        return delegate.getAttributes()
    }

    override fun getSections(): MutableMap<String?, Attributes?>? {
        return delegate.getSections()
    }

    @Throws(ManifestException::class)
    override fun attributes(attributes: MutableMap<String?, *>?): Manifest {
        delegate.attributes(attributes)
        return this
    }

    @Throws(ManifestException::class)
    override fun attributes(attributes: MutableMap<String?, *>?, sectionName: String?): Manifest {
        delegate.attributes(attributes, sectionName)
        return this
    }

    override fun getEffectiveManifest(): Manifest? {
        return delegate.getEffectiveManifest()
    }

    override fun getContentCharsetProvider(): Provider<String?>? {
        return contentCharset
    }

    override fun writeTo(path: Any?): Manifest {
        delegate.writeTo(path)
        return this
    }

    override fun from(vararg mergePath: Any?): Manifest {
        delegate.from(*mergePath)
        return this
    }

    override fun from(mergePath: Any?, closure: Closure<*>?): Manifest {
        delegate.from(mergePath, closure)
        return this
    }

    override fun from(mergePath: Any?, action: Action<ManifestMergeSpec?>?): Manifest {
        delegate.from(mergePath, action)
        return this
    }
}
