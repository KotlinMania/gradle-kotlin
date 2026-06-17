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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.base.Objects
import org.gradle.internal.FileUtils
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeType
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.ComplexExpression
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.SimpleExpression
import java.io.File
import java.util.function.Function

class DefaultSourceIncludesResolver(includePaths: MutableList<File>, private val fileSystemAccess: FileSystemAccess) : SourceIncludesResolver {
    private val includeRoots: MutableMap<File?, DirectoryContents?> = HashMap<File?, DirectoryContents?>()
    private val includePath: FixedIncludePath

    init {
        val includeDirs: MutableList<DirectoryContents> = ArrayList<DirectoryContents>(includePaths.size)
        for (includeDir in includePaths) {
            includeDirs.add(toDir(includeDir))
        }
        this.includePath = FixedIncludePath(includeDirs)
    }

    override fun resolveInclude(sourceFile: File, include: Include, visibleMacros: MacroLookup): SourceIncludesResolver.IncludeResolutionResult {
        val results = BuildableResult()
        resolveExpression(visibleMacros, include, DefaultSourceIncludesResolver.PathResolvingVisitor(sourceFile, results), TokenLookup())
        return results
    }

    private fun resolveExpression(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        if (expression.getType() == IncludeType.SYSTEM) {
            visitor.visitSystem(expression)
        } else if (expression.getType() == IncludeType.QUOTED) {
            visitor.visitQuoted(expression)
        } else if (expression.getType() == IncludeType.IDENTIFIER) {
            visitor.visitIdentifier(expression)
        } else if (expression.getType() == IncludeType.ARGS_LIST) {
            visitor.visitTokens(expression)
        } else {
            if (!visitor.startVisit(expression)) {
                // Skip, visitor is not interested
                return
            }
            if (expression.getType() == IncludeType.MACRO) {
                resolveMacro(visibleMacros, expression, visitor, tokenLookup)
            } else if (expression.getType() == IncludeType.MACRO_FUNCTION) {
                resolveMacroFunction(visibleMacros, expression, visitor, tokenLookup)
            } else if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
                resolveTokenConcatenation(visibleMacros, expression, visitor, tokenLookup)
            } else if (expression.getType() == IncludeType.EXPAND_TOKEN_CONCATENATION) {
                resolveAndExpandTokenConcatenation(visibleMacros, expression, visitor, tokenLookup)
            } else if (expression.getType() == IncludeType.EXPRESSIONS) {
                resolveExpressionSequence(visibleMacros, expression, visitor, tokenLookup)
            } else {
                visitor.visitUnresolved()
            }
        }
    }

    private fun resolveExpressionSequence(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        val expressions = expression.getArguments()
        // Only <function-call>+ <args-list> supported
        if (expressions.size < 2) {
            visitor.visitUnresolved()
            return
        }
        val argListExpression = expressions.get(expressions.size - 1)
        val headExpressions = expressions.subList(0, expressions.size - 1)
        val args = resolveExpressionToTokens(visibleMacros, argListExpression, visitor, tokenLookup)
        for (value in args) {
            resolveExpressionSequenceForArgs(visibleMacros, headExpressions, value, visitor, tokenLookup)
        }
    }

    private fun resolveExpressionSequenceForArgs(visibleMacros: MacroLookup, expressions: MutableList<Expression>, args: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        if (args.getType() != IncludeType.ARGS_LIST) {
            visitor.visitUnresolved()
            return
        }

        val macroFunctionExpression = expressions.get(expressions.size - 1)
        val headExpressions = expressions.subList(0, expressions.size - 1)
        val identifiers = resolveExpressionToTokens(visibleMacros, macroFunctionExpression, visitor, tokenLookup)
        for (value in identifiers) {
            if (value.getType() != IncludeType.IDENTIFIER) {
                visitor.visitUnresolved()
                continue
            }
            val macroExpression = ComplexExpression(IncludeType.MACRO_FUNCTION, value.getValue(), args.getArguments())
            if (headExpressions.isEmpty()) {
                resolveExpression(visibleMacros, macroExpression, visitor, tokenLookup)
                return
            }
            val resolved = resolveExpressionToTokens(visibleMacros, macroExpression, visitor, tokenLookup)
            for (newArgs in resolved) {
                resolveExpressionSequenceForArgs(visibleMacros, headExpressions, newArgs, visitor, tokenLookup)
            }
        }
    }

    private fun resolveTokenConcatenation(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        val expressions = resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup)
        for (concatExpression in expressions) {
            resolveExpression(visibleMacros, concatExpression, visitor, tokenLookup)
        }
    }

    private fun resolveAndExpandTokenConcatenation(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        val expressions = resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup)
        for (concatExpression in expressions) {
            resolveExpression(visibleMacros, concatExpression.asMacroExpansion(), visitor, tokenLookup)
        }
    }

    /**
     * Resolves the given expression to zero or more expressions that have been macro expanded and token concatenated.
     */
    private fun resolveExpressionToTokens(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup): MutableCollection<Expression> {
        if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
            return resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup)
        }
        if (expression.getType() != IncludeType.MACRO && expression.getType() != IncludeType.MACRO_FUNCTION && expression.getType() != IncludeType.EXPAND_TOKEN_CONCATENATION) {
            return mutableListOf<Expression?>(expression)
        }

        // Otherwise, macro or macro function call
        if (!tokenLookup.hasTokensFor(expression)) {
            resolveExpression(visibleMacros, expression, CollectTokens(tokenLookup, expression), tokenLookup)
        }
        if (tokenLookup.isUnresolved(expression)) {
            visitor.visitUnresolved()
        }
        return tokenLookup.tokensFor(expression)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun resolveTokenConcatenationToTokens(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup): MutableCollection<Expression> {
        val left = expression.getArguments().get(0)
        val right = expression.getArguments().get(1)

        val leftValues = resolveExpressionToTokens(visibleMacros, left, visitor, tokenLookup)
        val rightValues = resolveExpressionToTokens(visibleMacros, right, visitor, tokenLookup)
        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return mutableListOf<Expression?>()
        }

        val expressions: MutableList<Expression> = ArrayList<Expression>(leftValues.size * rightValues.size)
        for (leftValue in leftValues) {
            if (leftValue.getType() != IncludeType.IDENTIFIER) {
                if (rightValues.size == 1) {
                    val rightValue = rightValues.iterator().next()
                    if (rightValue.getType() == IncludeType.EXPRESSIONS && rightValue.getArguments().isEmpty()) {
                        // Empty RHS
                        expressions.add(leftValue)
                        continue
                    }
                }
                // Not supported for now
                visitor.visitUnresolved()
                continue
            }
            val leftString = leftValue.getValue()
            for (rightValue in rightValues) {
                // Handle just empty string, single identifier or '(' params? ')', should handle more by parsing the tokens into an expression
                if (rightValue.getType() == IncludeType.IDENTIFIER) {
                    expressions.add(SimpleExpression(leftString + rightValue.getValue(), IncludeType.IDENTIFIER))
                    continue
                }
                if (rightValue.getType() == IncludeType.ARGS_LIST) {
                    expressions.add(ComplexExpression(IncludeType.MACRO_FUNCTION, leftString, rightValue.getArguments()))
                    continue
                }
                if (rightValue.getType() == IncludeType.EXPRESSIONS && rightValue.getArguments().isEmpty()) {
                    expressions.add(SimpleExpression(leftString, IncludeType.IDENTIFIER))
                    continue
                }
                visitor.visitUnresolved()
            }
        }
        return expressions
    }

    private fun resolveMacro(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        var found = false
        for (includeDirectives in visibleMacros) {
            val macros = includeDirectives.getMacros(expression.getValue())
            for (macro in macros) {
                found = true
                resolveExpression(visibleMacros, macro, visitor, tokenLookup)
            }
        }
        if (!found) {
            visitor.visitIdentifier(SimpleExpression(expression.getValue(), IncludeType.IDENTIFIER))
        }
    }

    private fun resolveMacroFunction(visibleMacros: MacroLookup, expression: Expression, visitor: ExpressionVisitor, tokenLookup: TokenLookup) {
        var found = false
        for (includeDirectives in visibleMacros) {
            val macroFunctions = includeDirectives.getMacroFunctions(expression.getValue())
            for (macro in macroFunctions) {
                var arguments = expression.getArguments()
                if (arguments.isEmpty() && macro.getParameterCount() == 1) {
                    // Provide an implicit empty argument
                    arguments = mutableListOf<Expression?>(SimpleExpression.Companion.EMPTY_EXPRESSIONS)
                }
                if (macro.getParameterCount() == arguments.size) {
                    found = true
                    val result = macro.evaluate(arguments)
                    resolveExpression(visibleMacros, result, visitor, tokenLookup)
                }
            }
        }
        if (!found) {
            visitor.visitUnresolved()
        }
    }

    override fun resolveInclude(sourceFile: File?, includePath: String?): SourceIncludesResolver.IncludeFile? {
        val path = if (sourceFile != null) prependSourceDir(sourceFile, this.includePath) else this.includePath
        return path.searchForDependency(includePath, sourceFile != null)
    }

    private fun toDir(includeDir: File): DirectoryContents {
        var directoryContents = includeRoots.get(includeDir)
        if (directoryContents == null) {
            directoryContents = DefaultSourceIncludesResolver.DirectoryContents(includeDir)
            includeRoots.put(includeDir, directoryContents)
        }
        return directoryContents!!
    }

    private fun prependSourceDir(sourceFile: File, includePaths: FixedIncludePath): IncludePath {
        val sourceDir = sourceFile.getParentFile()
        if (includePaths.startsWith(sourceDir)) {
            // Source dir already at the start of the path, just use the include path
            return includePaths
        }
        return PrefixedIncludePath(toDir(sourceDir), includePaths)
    }

    private abstract class IncludePath {
        abstract fun searchForDependency(includePath: String?, quotedPath: Boolean): SourceIncludesResolver.IncludeFile?
    }

    private class PrefixedIncludePath(private val head: DirectoryContents, private val tail: IncludePath) : IncludePath() {
        override fun searchForDependency(includePath: String, quotedPath: Boolean): SourceIncludesResolver.IncludeFile? {
            val includeFile = head.get(includePath)
            if (includeFile.type == FileType.RegularFile) {
                return includeFile.toIncludeFile(quotedPath)
            }
            return tail.searchForDependency(includePath, quotedPath)
        }
    }

    private class FixedIncludePath(private val directories: MutableList<DirectoryContents>) : IncludePath() {
        private val cachedLookups: MutableMap<String?, CachedIncludeFile?> = HashMap<String?, CachedIncludeFile?>()

        override fun searchForDependency(includePath: String, quotedPath: Boolean): SourceIncludesResolver.IncludeFile? {
            var includeFile = cachedLookups.get(includePath)
            if (includeFile == null) {
                for (dir in directories) {
                    includeFile = dir.get(includePath)
                    if (includeFile.type == FileType.RegularFile) {
                        break
                    }
                }
                if (includeFile == null) {
                    includeFile = MISSING_INCLUDE_FILE
                }
                cachedLookups.put(includePath, includeFile)
            }
            if (includeFile.type == FileType.RegularFile) {
                return includeFile.toIncludeFile(quotedPath)
            }
            return null
        }

        fun startsWith(sourceDir: File?): Boolean {
            return directories.size > 0 && directories.get(0).searchDir == sourceDir
        }
    }

    private inner class DirectoryContents(private val searchDir: File) {
        private val contents: MutableMap<String?, CachedIncludeFile> = HashMap<String?, CachedIncludeFile>()

        fun get(includePath: String): CachedIncludeFile {
            return contents.computeIfAbsent(
                includePath
            ) { key: String? ->
                val candidate = normalizeIncludePath(searchDir, includePath)
                fileSystemAccess.readRegularFileContentHash(candidate.getAbsolutePath())
                    .map<CachedIncludeFile?>(Function { contentHash: HashCode? -> DefaultSourceIncludesResolver.SystemIncludeFile(candidate, key, contentHash!!) as CachedIncludeFile })
                    .orElse(MISSING_INCLUDE_FILE)
            }
        }
    }

    private fun normalizeIncludePath(searchDir: File?, prefixPath: String): File {
        var onlyDotsSinceLastSeparator = true
        for (i in 0..<prefixPath.length) {
            val currentChar = prefixPath.get(i)
            if (currentChar == '/' || currentChar == '\\') {
                if (onlyDotsSinceLastSeparator) {
                    return FileUtils.normalize(File(searchDir, prefixPath))
                }
                onlyDotsSinceLastSeparator = true
            } else {
                if (currentChar != '.') {
                    onlyDotsSinceLastSeparator = false
                }
            }
        }
        return File(searchDir, prefixPath)
    }

    private abstract class CachedIncludeFile {
        abstract val type: FileType?

        abstract fun toIncludeFile(quotedPath: Boolean): SourceIncludesResolver.IncludeFile?
    }

    private class MissingIncludeFile : CachedIncludeFile() {
        override fun getType(): FileType {
            return FileType.Missing
        }

        override fun toIncludeFile(quotedPath: Boolean): SourceIncludesResolver.IncludeFile? {
            throw UnsupportedOperationException()
        }
    }

    private open class SystemIncludeFile(val file: File?, val includePath: String?, val contentHash: HashCode) : CachedIncludeFile(), SourceIncludesResolver.IncludeFile {
        override fun getPath(): String? {
            return includePath
        }

        override fun isQuotedInclude(): Boolean {
            return false
        }

        override fun getFile(): File? {
            return file
        }

        override fun getType(): FileType {
            return FileType.RegularFile
        }

        override fun getContentHash(): HashCode {
            return contentHash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || javaClass != obj.javaClass) {
                return false
            }
            val other = obj as SystemIncludeFile
            return Objects.equal(file, other.file) && contentHash == other.contentHash
        }

        override fun hashCode(): Int {
            return contentHash.hashCode()
        }

        override fun toIncludeFile(quotedPath: Boolean): SourceIncludesResolver.IncludeFile {
            if (quotedPath) {
                return QuotedIncludeFile(file, includePath, contentHash)
            }
            return this
        }

        private class QuotedIncludeFile(file: File?, includePath: String?, contentHash: HashCode) : SystemIncludeFile(file, includePath, contentHash) {
            override fun isQuotedInclude(): Boolean {
                return true
            }
        }
    }

    private interface ExpressionVisitor {
        /**
         * Called when an expression is about to be visited. Called for each intermediate expression as macros are expanded.
         *
         * @return true if the visit should continue, false to skip this expression.
         */
        fun startVisit(expression: Expression?): Boolean

        /**
         * Called when an expression resolves to a quoted path.
         */
        fun visitQuoted(value: Expression?)

        /**
         * Called when an expression resolves to a system path.
         */
        fun visitSystem(value: Expression?)

        /**
         * Called when an expression resolves to a single identifier that could not be macro expanded.
         */
        fun visitIdentifier(value: Expression?)

        /**
         * Called when an expression resolves to zero or more tokens.
         */
        fun visitTokens(tokens: Expression?)

        /**
         * Called when an expression could not be resolved to a value.
         */
        fun visitUnresolved()
    }

    private class BuildableResult : SourceIncludesResolver.IncludeResolutionResult {
        private val files: MutableSet<SourceIncludesResolver.IncludeFile?> = LinkedHashSet<SourceIncludesResolver.IncludeFile?>()
        private var missing = false

        fun resolved(includeFile: SourceIncludesResolver.IncludeFile?) {
            files.add(includeFile)
        }

        fun unresolved() {
            missing = true
        }

        override fun isComplete(): Boolean {
            return !missing
        }

        override fun getFiles(): MutableSet<SourceIncludesResolver.IncludeFile?> {
            return files
        }
    }

    private class CollectTokens(private val tokenLookup: TokenLookup, private val expression: Expression?) : ExpressionVisitor {
        private val seen: MutableSet<Expression?> = HashSet<Expression?>()

        override fun startVisit(expression: Expression?): Boolean {
            return seen.add(expression)
        }

        override fun visitQuoted(value: Expression?) {
            visitTokens(value)
        }

        override fun visitSystem(value: Expression?) {
            visitTokens(value)
        }

        override fun visitIdentifier(value: Expression?) {
            visitTokens(value)
        }

        override fun visitTokens(tokens: Expression?) {
            tokenLookup.addTokensFor(expression, tokens)
        }

        override fun visitUnresolved() {
            tokenLookup.unresolved(expression)
        }
    }

    private inner class PathResolvingVisitor(private val sourceFile: File, private val results: BuildableResult) : ExpressionVisitor {
        private val seen: MutableSet<Expression?> = HashSet<Expression?>()

        var quoted: MutableSet<String?> = HashSet<String?>()
        var system: MutableSet<String?> = HashSet<String?>()

        override fun startVisit(expression: Expression?): Boolean {
            return seen.add(expression)
        }

        override fun visitQuoted(value: Expression) {
            val path = value.getValue()
            if (!quoted.add(path)) {
                return
            }
            val quotedSearchPath = prependSourceDir(sourceFile, includePath)
            val includeFile = quotedSearchPath.searchForDependency(path, true)
            if (includeFile != null) {
                results.resolved(includeFile)
            }
        }

        override fun visitSystem(value: Expression) {
            val path = value.getValue()
            if (!system.add(path)) {
                return
            }
            val includeFile = includePath.searchForDependency(path, false)
            if (includeFile != null) {
                results.resolved(includeFile)
            }
        }

        override fun visitIdentifier(value: Expression?) {
            results.unresolved()
        }

        override fun visitTokens(tokens: Expression?) {
            results.unresolved()
        }

        override fun visitUnresolved() {
            results.unresolved()
        }
    }

    companion object {
        private val MISSING_INCLUDE_FILE = MissingIncludeFile()
    }
}
