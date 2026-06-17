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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Builder for the properly order list of [generation plans][GenerationPlan].
 *
 *
 * IMPL NOTE : Uses recursive calls to achieve ordering.
 */
class GenerationPlanBuilder(private val outputDirectory: File?) {
    private val generationPlans = LinkedHashMap<String?, GenerationPlan?>()

    private var metadataXRef: XRef? = null

    @Synchronized
    fun buildGenerationPlans(metadataXRef: XRef): MutableList<GenerationPlan?> {
        this.metadataXRef = metadataXRef

        val grammarFiles = metadataXRef.iterateGrammarFiles()
        while (grammarFiles.hasNext()) {
            val grammarFileMetadata = grammarFiles.next()
            // NOTE : locateOrBuildGenerationPlan populates the generationPlans map
            locateOrBuildGenerationPlan(grammarFileMetadata)
        }

        return ArrayList<GenerationPlan?>(generationPlans.values)
    }

    private fun locateOrBuildGenerationPlan(grammarFileMetadata: GrammarFileMetadata): GenerationPlan {
        var generationPlan = generationPlans.get(grammarFileMetadata.getFilePath().getPath())
        if (generationPlan == null) {
            generationPlan = buildGenerationPlan(grammarFileMetadata)
        }
        return generationPlan
    }

    private fun buildGenerationPlan(grammarFileMetadata: GrammarFileMetadata): GenerationPlan {
        val generationDirectory = if (isEmpty(grammarFileMetadata.getPackageName())) outputDirectory else File(
            outputDirectory, grammarFileMetadata.getPackageName().replace('.', File.separatorChar)
        )

        val generationPlan = GenerationPlan(grammarFileMetadata.getFilePath(), generationDirectory)

        for (grammarMetadata in grammarFileMetadata.getGrammars()) {
            val generatedParserFile = File(outputDirectory, grammarMetadata.determineGeneratedParserPath())

            if (!generatedParserFile.exists()) {
                generationPlan.markOutOfDate()
            } else if (generatedParserFile.lastModified() < generationPlan.getSource().lastModified()) {
                generationPlan.markOutOfDate()
            }

            // see if the grammar if out-of-date by way of its super-grammar(s) as gleaned from parsing the grammar file
            if (!grammarMetadata.extendsStandardGrammar()) {
                val superGrammarGrammarFileMetadata = grammarMetadata.getSuperGrammarDelegate()
                    .getAssociatedGrammarMetadata().getGrammarFile()
                if (superGrammarGrammarFileMetadata != null) {
                    val superGrammarGenerationPlan = locateOrBuildGenerationPlan(
                        superGrammarGrammarFileMetadata
                    )
                    if (superGrammarGenerationPlan.isOutOfDate()) {
                        generationPlan.markOutOfDate()
                    } else if (superGrammarGenerationPlan.getSource().lastModified() > generatedParserFile
                            .lastModified()
                    ) {
                        generationPlan.markOutOfDate()
                    }
                }
            }

            // see if the grammar if out-of-date by way of its importVocab
            if (isNotEmpty(grammarMetadata.getImportVocab())) {
                val importVocabGrammarFileMetadata = metadataXRef!!.getGrammarFileByExportVocab(
                    grammarMetadata.getImportVocab()
                )
                if (importVocabGrammarFileMetadata == null) {
                    LOGGER.warn(
                        ("unable to locate grammar exporting specified import vocab ["
                                + grammarMetadata.getImportVocab() + "]")
                    )
                } else if (importVocabGrammarFileMetadata.getFilePath() != grammarFileMetadata.getFilePath()) {
                    val importVocabGrammarGenerationPlan = locateOrBuildGenerationPlan(
                        importVocabGrammarFileMetadata
                    )
                    generationPlan.setImportVocabTokenTypesDirectory(
                        importVocabGrammarGenerationPlan.getGenerationDirectory()
                    )
                    if (importVocabGrammarGenerationPlan.isOutOfDate()) {
                        generationPlan.markOutOfDate()
                    } else if (importVocabGrammarGenerationPlan.getSource().lastModified() > generatedParserFile
                            .lastModified()
                    ) {
                        generationPlan.markOutOfDate()
                    }
                }
            }
        }

        generationPlans.put(generationPlan.getId(), generationPlan)
        return generationPlan
    }

    private fun isEmpty(string: String?): Boolean {
        return string == null || string.trim { it <= ' ' }.length == 0
    }

    private fun isNotEmpty(string: String?): Boolean {
        return !isEmpty(string)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GenerationPlanBuilder::class.java)
    }
}
