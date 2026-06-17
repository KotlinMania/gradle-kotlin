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

import antlr.preprocessor.Hierarchy

/**
 * Models cross-reference (x-ref) info about [grammar files][GrammarFileMetadata] such as [.filesByPath], [.filesByExportVocab] and [.filesByClassName].
 */
class XRef(private val antlrHierarchy: Hierarchy?) {
    private val filesByPath = LinkedHashMap<String?, GrammarFileMetadata?>()
    private val filesByExportVocab = HashMap<String?, GrammarFileMetadata?>()
    private val filesByClassName = HashMap<String?, GrammarFileMetadata?>()

    fun getAntlrHierarchy(): Any? {
        return antlrHierarchy
    }

    /**
     * Adds a grammar file to this cross-reference.
     *
     * @param grammarFileMetadata The grammar file to add (and to be cross referenced).
     */
    fun addGrammarFile(grammarFileMetadata: GrammarFileMetadata) {
        filesByPath.put(grammarFileMetadata.getFilePath().getPath(), grammarFileMetadata)
        for (grammarMetadata in grammarFileMetadata.getGrammars()) {
            filesByClassName.put(grammarMetadata.getClassName(), grammarFileMetadata)
            val exportVocabName = if (grammarMetadata.getExportVocab() != null) grammarMetadata.getExportVocab() else grammarMetadata.getClassName()
            val old = filesByExportVocab.put(exportVocabName, grammarFileMetadata)
            if (old != null && old !== grammarFileMetadata) {
                println("[WARNING] : multiple grammars defined the same exportVocab : " + exportVocabName)
            }
        }
    }

    fun iterateGrammarFiles(): MutableIterator<GrammarFileMetadata?> {
        return filesByPath.values.iterator()
    }

    /**
     * Locate the grammar file metadata by grammar file path.
     *
     * @param path The grammar file path.
     * @return The grammar file metadata.  May be null if none found.
     */
    fun getGrammarFileByPath(path: String?): GrammarFileMetadata? {
        return filesByPath.get(path)
    }

    /**
     * Locate the grammar file metadata by the name of a class generated from one of its included grammars.
     *
     * @param className The generated class name.
     * @return The grammar file metadata.  May be null if none found.
     */
    fun getGrammarFileByClassName(className: String?): GrammarFileMetadata? {
        return filesByClassName.get(className)
    }

    /**
     * Locate the grammar file metadata by the name of a vocabulary exported from one of its included grammars.
     *
     * @param vocabName The vocabulary name
     * @return The grammar file metadata.  May be null if none found.
     */
    fun getGrammarFileByExportVocab(vocabName: String?): GrammarFileMetadata? {
        return filesByExportVocab.get(vocabName)
    }
}
