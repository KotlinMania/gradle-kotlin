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
package org.gradle.api.java.archives

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.internal.HasInternalProtocol
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Represents the manifest file of a JAR file.
 */
@HasInternalProtocol
interface Manifest {
    @get:ToBeReplacedByLazyProperty
    val attributes: Attributes?

    @get:ToBeReplacedByLazyProperty
    val sections: MutableMap<String?, Attributes?>?

    /**
     * Adds content to the main attributes of the manifest.
     *
     * @param attributes The values to add to the main attributes. The values can be any object. For evaluating the value objects
     * their [Object.toString] method is used. This is done lazily either before writing or when [.getEffectiveManifest]
     * is called.
     *
     * @return this
     * @throws ManifestException If a key is invalid according to the manifest spec or if a key or value is null.
     */
    @Throws(ManifestException::class)
    fun attributes(attributes: MutableMap<String?, *>?): Manifest?

    /**
     * Adds content to the given section of the manifest.
     *
     * @param attributes The values to add to the section. The values can be any object. For evaluating the value objects
     * their [Object.toString] method is used. This is done lazily either before writing or when [.getEffectiveManifest]
     * is called.
     * @param sectionName The name of the section
     *
     * @return this
     * @throws ManifestException If a key is invalid according to the manifest spec or if a key or value is null.
     */
    @Throws(ManifestException::class)
    fun attributes(attributes: MutableMap<String?, *>?, sectionName: String?): Manifest?

    /**
     * Returns a new manifest instance where all the attribute values are expanded (e.g. their toString method is called).
     * The returned manifest also contains all the attributes of the to be merged manifests specified in [.from].
     */
    val effectiveManifest: Manifest?

    /**
     * Writes the manifest into a file. The path's are resolved as defined by [org.gradle.api.Project.files]
     *
     * The manifest will be encoded using the character set defined by the [org.gradle.jvm.tasks.Jar.getManifestContentCharset] property.
     *
     * @param path The path of the file to write the manifest into.
     * @return this
     */
    fun writeTo(path: Any?): Manifest?

    /**
     * Specifies other manifests to be merged into this manifest. A merge path can either be another instance of
     * [Manifest] or a file path as interpreted by [org.gradle.api.Project.file].
     *
     * The merge is not happening instantaneously. It happens either before writing or when [.getEffectiveManifest]
     * is called.
     *
     * @return this
     */
    fun from(vararg mergePath: Any?): Manifest?

    /**
     * Specifies other manifests to be merged into this manifest. A merge path is interpreted as described in
     * [.from].
     *
     * The merge is not happening instantaneously. It happens either before writing or when [.getEffectiveManifest]
     * is called.
     *
     * The closure configures the underlying [ManifestMergeSpec].
     *
     * @return this
     */
    fun from(mergePath: Any?, @DelegatesTo(ManifestMergeSpec::class) closure: Closure<*>?): Manifest?

    /**
     * Specifies other manifests to be merged into this manifest. A merge path is interpreted as described in
     * [.from].
     *
     * The merge is not happening instantaneously. It happens either before writing or when [.getEffectiveManifest]
     * is called.
     *
     * @return this
     * @since 5.0
     */
    fun from(mergePath: Any?, action: Action<ManifestMergeSpec?>?): Manifest?
}
