/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal

import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.nativeplatform.internal.AbstractBinaryToolSpec
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import java.io.File
import java.util.Collections

abstract class AbstractNativeCompileSpec : AbstractBinaryToolSpec(), NativeCompileSpec {
    private val includeRoots: MutableList<File?> = ArrayList<File?>()
    private val systemIncludeRoots: MutableList<File?> = ArrayList<File?>()
    private val sourceFiles: MutableList<File?> = ArrayList<File?>()
    private val removedSourceFiles: MutableList<File?> = ArrayList<File?>()
    private var incrementalCompile = false
    private var macros: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
    private var objectFileDir: File? = null
    private var positionIndependentCode = false
    private var debuggable = false
    private var optimized = false
    private var oplogger: BuildOperationLogger? = null
    private var prefixHeaderFile: File? = null
    private var preCompiledHeaderObjectFile: File? = null
    private var sourceFilesForPch: MutableList<File?>? = ArrayList<File?>()
    private var preCompiledHeader: String? = null

    override fun getIncludeRoots(): MutableList<File?> {
        return includeRoots
    }

    override fun include(vararg includeRoots: File?) {
        Collections.addAll<File?>(this.includeRoots, *includeRoots)
    }

    override fun include(includeRoots: Iterable<File?>) {
        addAll(this.includeRoots, includeRoots)
    }

    override fun getSystemIncludeRoots(): MutableList<File?> {
        return systemIncludeRoots
    }

    override fun systemInclude(systemIncludeRoots: Iterable<File?>) {
        addAll(this.systemIncludeRoots, systemIncludeRoots)
    }

    override fun getSourceFiles(): MutableList<File?> {
        return sourceFiles
    }

    override fun source(sources: Iterable<File?>) {
        addAll(sourceFiles, sources)
    }

    override fun setSourceFiles(sources: MutableCollection<File?>) {
        sourceFiles.clear()
        sourceFiles.addAll(sources)
    }

    override fun getRemovedSourceFiles(): MutableList<File?> {
        return removedSourceFiles
    }

    override fun removedSource(sources: Iterable<File?>) {
        addAll(removedSourceFiles, sources)
    }

    override fun setRemovedSourceFiles(sources: MutableCollection<File?>) {
        removedSourceFiles.clear()
        removedSourceFiles.addAll(sources)
    }

    override fun isIncrementalCompile(): Boolean {
        return incrementalCompile
    }

    override fun setIncrementalCompile(flag: Boolean) {
        incrementalCompile = flag
    }

    override fun getObjectFileDir(): File? {
        return objectFileDir
    }

    override fun setObjectFileDir(objectFileDir: File?) {
        this.objectFileDir = objectFileDir
    }

    override fun getMacros(): MutableMap<String?, String?> {
        return macros
    }

    override fun setMacros(macros: MutableMap<String?, String?>) {
        this.macros = macros
    }

    override fun define(name: String?) {
        macros.put(name, null)
    }

    override fun define(name: String?, value: String?) {
        macros.put(name, value)
    }

    override fun isPositionIndependentCode(): Boolean {
        return positionIndependentCode
    }

    override fun setPositionIndependentCode(positionIndependentCode: Boolean) {
        this.positionIndependentCode = positionIndependentCode
    }

    override fun isDebuggable(): Boolean {
        return debuggable
    }

    override fun setDebuggable(debuggable: Boolean) {
        this.debuggable = debuggable
    }

    override fun isOptimized(): Boolean {
        return optimized
    }

    override fun setOptimized(optimized: Boolean) {
        this.optimized = optimized
    }

    override fun getPreCompiledHeaderObjectFile(): File? {
        return preCompiledHeaderObjectFile
    }

    override fun setPreCompiledHeaderObjectFile(preCompiledHeaderObjectFile: File?) {
        this.preCompiledHeaderObjectFile = preCompiledHeaderObjectFile
    }

    override fun getPrefixHeaderFile(): File? {
        return prefixHeaderFile
    }

    override fun setPrefixHeaderFile(pchFile: File?) {
        this.prefixHeaderFile = pchFile
    }

    override fun getPreCompiledHeader(): String? {
        return preCompiledHeader
    }

    override fun setPreCompiledHeader(preCompiledHeader: String?) {
        this.preCompiledHeader = preCompiledHeader
    }

    private fun addAll(list: MutableList<File?>, iterable: Iterable<File?>) {
        for (file in iterable) {
            list.add(file)
        }
    }

    override fun getOperationLogger(): BuildOperationLogger? {
        return oplogger
    }

    override fun setOperationLogger(oplogger: BuildOperationLogger?) {
        this.oplogger = oplogger
    }

    override fun getSourceFilesForPch(): MutableList<File?>? {
        return sourceFilesForPch
    }

    override fun setSourceFilesForPch(sourceFilesForPch: MutableList<File?>?) {
        this.sourceFilesForPch = sourceFilesForPch
    }
}
