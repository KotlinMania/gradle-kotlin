/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.language.swift.tasks.internal

import java.io.File
import java.io.IOException
import java.lang.Byte
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import kotlin.ByteArray
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Throws

/**
 * Parse and hide given symbols in on object file based on COFF format documented
 * here: https://docs.microsoft.com/en-us/windows/desktop/debug/pe-format
 */
class SymbolHider(inputFile: File) {
    var data: DataReader? = null
    private val objectBytes: ByteArray

    private class DataReader(private val dataBytes: ByteArray) {
        var position: Int = 0
            private set

        fun moveTo(position: Int) {
            this.position = position
        }

        fun readByte(): Int {
            return Byte.toUnsignedInt(dataBytes[position++])
        }

        fun readWord(): Int {
            return readByte() or (readByte() shl 8)
        }

        fun readDoubleWord(): Int {
            return readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)
        }

        fun readBytes(count: Int): ByteArray {
            val sub = Arrays.copyOfRange(dataBytes, position, position + count)
            position += count
            return sub
        }
    }

    @Suppress("unused") // members are used for dummy reads, might come in handy for debugging
    private class COFFHeader(data: DataReader) {
        var machine: Int
        var numberOfSections: Int
        var timeDateStamp: Int
        var pointerToSymbolTable: Int
        var numberOfSymbols: Int
        var sizeOfOptionalHeader: Int
        var characteristics: Int

        init {
            machine = data.readWord()
            numberOfSections = data.readWord()
            timeDateStamp = data.readDoubleWord()
            pointerToSymbolTable = data.readDoubleWord()
            numberOfSymbols = data.readDoubleWord()
            sizeOfOptionalHeader = data.readWord()
            characteristics = data.readWord()
        }
    }

    @Suppress("unused") // members are used for dummy reads, might come in handy for debugging
    private class SymbolRecord(data: DataReader) {
        private val storageClass: Int
        private val name: ByteArray
        private val value: Int
        private val sectionNumber: Int
        private val type: Int
        private val numberOfAuxSymbols: Int

        init {
            name = data.readBytes(8)
            value = data.readDoubleWord()
            sectionNumber = data.readWord()
            type = data.readWord()
            storageClass = data.readByte()
            numberOfAuxSymbols = data.readByte()
        }

        fun getName(): String {
            // We only need to hide "main", so only support short named symbols here.
            var nullCharIndex = 0
            nullCharIndex = 0
            while (nullCharIndex < name.size) {
                if (name[nullCharIndex].toInt() == 0) {
                    break
                }
                ++nullCharIndex
            }
            return String(name, 0, nullCharIndex, StandardCharsets.UTF_8)
        }
    }

    private class SymbolTable(private val numberOfSymbols: Int, private val data: DataReader, private val objectBytes: ByteArray) {
        fun hideSymbol(symbolToHide: String?) {
            for (i in 0..<numberOfSymbols) {
                val symbol = SymbolRecord(data)
                val name = symbol.getName()

                if (name == symbolToHide) {
                    objectBytes[data.position - 2] = IMAGE_SYM_CLASS_STATIC.toByte()
                    break
                }
            }
        }

        companion object {
            private const val IMAGE_SYM_CLASS_STATIC = 0x3
        }
    }

    init {
        objectBytes = Files.readAllBytes(Paths.get(inputFile.getAbsolutePath()))
    }

    fun hideSymbol(symbolToHide: String?) {
        data = DataReader(objectBytes)
        val coffHeader = SymbolHider.COFFHeader(data!!)

        data!!.moveTo(coffHeader.pointerToSymbolTable)
        val symbolTable = SymbolHider.SymbolTable(coffHeader.numberOfSymbols, data!!, objectBytes)
        symbolTable.hideSymbol(symbolToHide)
    }

    @Throws(IOException::class)
    fun saveTo(outputFile: File) {
        Files.write(Paths.get(outputFile.getAbsolutePath()), objectBytes)
    }
}
