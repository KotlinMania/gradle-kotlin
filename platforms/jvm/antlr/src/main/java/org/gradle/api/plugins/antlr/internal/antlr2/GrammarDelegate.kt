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

import antlr.collections.impl.IndexedVector
import antlr.preprocessor.GrammarFile
import java.lang.reflect.Method

/**
 * Antlr defines its [antlr.preprocessor.Grammar] class as package-protected for some unfortunate reason. So this class acts as a delegate to the Antlr [antlr.preprocessor.Grammar] class,
 * hiding all the ugly necessary reflection code.
 */
class GrammarDelegate(antlrGrammarMetadata: Any?) {
    /**
     * Retrieves the unqualified name of the lexer/parser class.
     *
     * @return The unqualified lexer/parser class name.
     */
    val className: String?

    /**
     * Retrieves the name of this vocabulary imported by this grammar.
     *
     * @return The grammar's imported vocabulary name.
     */
    val importVocab: String?

    /**
     * Retrieves the name of this vocabulary exported by this grammar.
     *
     * @return The grammar's exported vocabulary name.
     */
    val exportVocab: String?

    /**
     * Retrieves the grammar delegate associated with this grammars super grammar deduced during preprocessing from its extends clause.
     *
     * @return The super-grammar grammar delegate
     */
    val superGrammarDelegate: GrammarDelegate?

    var associatedGrammarMetadata: GrammarMetadata? = null
        private set

    fun associateWith(associatedGrammarMetadata: GrammarMetadata?) {
        this.associatedGrammarMetadata = associatedGrammarMetadata
    }

    private fun vocabName(vocabName: String?): String? {
        var vocabName = vocabName
        if (vocabName == null) {
            return null
        }
        vocabName = vocabName.trim { it <= ' ' }
        if (vocabName.endsWith(";")) {
            vocabName = vocabName.substring(0, vocabName.length - 1)
        }
        return vocabName
    }

    init {
        try {
            val getNameMethod: Method = ANTLR_GRAMMAR_CLASS.getDeclaredMethod("getName", *NO_ARG_SIGNATURE)
            getNameMethod.setAccessible(true)
            this.className = getNameMethod.invoke(antlrGrammarMetadata, *NO_ARGS) as String?

            val getSuperGrammarMethod: Method = ANTLR_GRAMMAR_CLASS.getMethod("getSuperGrammar", *NO_ARG_SIGNATURE)
            getSuperGrammarMethod.setAccessible(true)
            val antlrSuperGrammarGrammarMetadata = getSuperGrammarMethod.invoke(antlrGrammarMetadata, *NO_ARGS)
            this.superGrammarDelegate = if (antlrSuperGrammarGrammarMetadata == null) null else GrammarDelegate(antlrSuperGrammarGrammarMetadata)

            val getOptionsMethod: Method = ANTLR_GRAMMAR_CLASS.getMethod("getOptions", *NO_ARG_SIGNATURE)
            getOptionsMethod.setAccessible(true)
            val options = getOptionsMethod.invoke(antlrGrammarMetadata, *NO_ARGS) as IndexedVector?

            val getRHSMethod: Method = ANTLR_OPTION_CLASS.getMethod("getRHS", *NO_ARG_SIGNATURE)
            getRHSMethod.setAccessible(true)

            val importVocabOption = if (options == null) null else options.getElement("importVocab")
            this.importVocab = if (importVocabOption == null) null else vocabName(getRHSMethod.invoke(importVocabOption, *NO_ARGS) as String?)

            val exportVocabOption = if (options == null) null else options.getElement("exportVocab")
            this.exportVocab = if (exportVocabOption == null) null else vocabName(getRHSMethod.invoke(exportVocabOption, *NO_ARGS) as String?)
        } catch (t: Throwable) {
            throw IllegalStateException("Error accessing  Antlr grammar metadata", t)
        }
    }

    companion object {
        fun extractGrammarDelegates(antlrGrammarFile: GrammarFile): MutableList<GrammarDelegate?> {
            val grammarDelegates: MutableList<GrammarDelegate?> = ArrayList<GrammarDelegate?>()
            val grammarFileGrammars = antlrGrammarFile.getGrammars().elements()
            while (grammarFileGrammars.hasMoreElements()) {
                grammarDelegates.add(GrammarDelegate(grammarFileGrammars.nextElement()))
            }
            return grammarDelegates
        }

        private val ANTLR_GRAMMAR_CLASS: Class<*>
        private val ANTLR_OPTION_CLASS: Class<*>

        init {
            ANTLR_GRAMMAR_CLASS = loadAntlrClass("antlr.preprocessor.Grammar")
            ANTLR_OPTION_CLASS = loadAntlrClass("antlr.preprocessor.Option")
        }

        val NO_ARG_SIGNATURE: Array<Class<*>?> = arrayOfNulls<Class<*>>(0)
        val NO_ARGS: Array<Any?> = arrayOfNulls<Any>(0)

        private fun loadAntlrClass(className: String?): Class<*> {
            try {
                return Class.forName(className, true, GrammarDelegate::class.java.getClassLoader())
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException("Unable to locate Antlr class [" + className + "]", e)
            }
        }
    }
}
