/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal.antlr2

import antlr.Parser
import antlr.TreeParser
import java.io.File

/**
 * Models a grammar defined within an Antlr grammar file.
 */
class GrammarMetadata(val grammarFile: GrammarFileMetadata?, private val grammarDelegate: GrammarDelegate) {
    init {
        grammarDelegate.associateWith(this)
    }

    val className: String
        get() = grammarDelegate.getClassName()

    val qualifiedClassName: String?
        get() {
            if (isEmpty(this.packageName)) {
                return this.className
            } else {
                return this.packageName + '.' + this.className
            }
        }

    val superGrammarDelegate: GrammarDelegate?
        get() = grammarDelegate.getSuperGrammarDelegate()

    fun extendsStandardGrammar(): Boolean {
        val superGrammarClassName = this.superGrammarDelegate!!.getClassName()
        return Parser::class.java.getName() == superGrammarClassName || Parser::class.java.getSimpleName() == superGrammarClassName || TreeParser::class.java.getName() == superGrammarClassName
                || TreeParser::class.java.getSimpleName() == superGrammarClassName || "Lexer" == superGrammarClassName
    }

    val importVocab: String?
        get() = grammarDelegate.getImportVocab()

    val exportVocab: String?
        get() = grammarDelegate.getExportVocab()

    val packageName: String?
        get() = this.grammarFile!!.getPackageName()

    /**
     * Determine the relative path of the generated parser java file.
     *
     * @return The relative generated parser file path.
     */
    fun determineGeneratedParserPath(): String {
        if (isEmpty(this.packageName)) {
            return this.className + ".java"
        } else {
            return this.packageName!!.replace('.', File.separatorChar) + File.separatorChar + this.className + ".java"
        }
    }

    private fun isEmpty(packageName: String?): Boolean {
        return packageName == null || packageName.trim { it <= ' ' }.length == 0
    }
}
