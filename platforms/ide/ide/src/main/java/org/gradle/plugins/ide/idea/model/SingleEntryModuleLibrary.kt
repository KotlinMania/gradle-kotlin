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
package org.gradle.plugins.ide.idea.model

import org.gradle.api.artifacts.ModuleVersionIdentifier

/**
 * Single entry module library
 */
class SingleEntryModuleLibrary : ModuleLibrary {
    /**
     * Module version of the library, if any.
     */
    var moduleVersion: ModuleVersionIdentifier? = null

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in idea format
     * @param javadoc paths to javadoc jars or javadoc folders
     * @param source paths to source jars or source folders
     * @param scope scope
     */
    constructor(library: FilePath?, javadoc: MutableSet<FilePath?>?, source: MutableSet<FilePath?>?, scope: String?) : super(
        mutableListOf<FilePath?>(library),
        javadoc,
        source,
        ArrayList<JarDirectory?>(),
        scope
    )

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in idea format
     * @param javadoc path to javadoc jars or javadoc folders
     * @param source paths to source jars or source folders
     * @param scope scope
     */
    constructor(library: FilePath?, javadoc: FilePath?, source: FilePath?, scope: String?) : super(
        mutableListOf<FilePath?>(library),
        if (javadoc != null) mutableListOf<FilePath?>(javadoc) else ArrayList<Path?>(),
        if (source != null) mutableListOf<FilePath?>(source) else ArrayList<Path?>(),
        LinkedHashSet<JarDirectory?>(),
        scope
    )

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in Path format
     * @param scope scope
     */
    constructor(library: FilePath?, scope: String?) : this(library, LinkedHashSet<FilePath?>(), LinkedHashSet<FilePath?>(), scope)

    val libraryFile: File?
        /**
         * Returns a single jar or class folder
         */
        get() = (this.getClasses().iterator().next() as FilePath).getFile()

    val javadocFile: File?
        /**
         * Returns a single javadoc jar or javadoc folder
         */
        get() {
            if (getJavadoc().size > 0) {
                return (this.getJavadoc().iterator().next() as FilePath).getFile()
            } else {
                return null
            }
        }

    val sourceFile: File?
        /**
         * Returns a single source jar or source folder
         */
        get() {
            if (getSources().size > 0) {
                return (this.getSources().iterator().next() as FilePath).getFile()
            } else {
                return null
            }
        }
}
