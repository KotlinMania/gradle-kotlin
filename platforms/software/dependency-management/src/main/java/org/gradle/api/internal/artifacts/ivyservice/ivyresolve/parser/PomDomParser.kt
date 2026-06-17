/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.LineNumberReader
import java.nio.charset.StandardCharsets
import java.util.LinkedList
import kotlin.math.min

object PomDomParser {
    fun getTextContent(element: Element): String {
        val result = StringBuilder()

        val childNodes = element.getChildNodes()
        for (i in 0..<childNodes.getLength()) {
            val child = childNodes.item(i)

            when (child.getNodeType()) {
                Node.CDATA_SECTION_NODE, Node.TEXT_NODE -> result.append(child.getNodeValue())
                else -> {}
            }
        }

        return result.toString()
    }

    fun getFirstChildText(parentElem: Element?, name: String): String? {
        val node = getFirstChildElement(parentElem, name)
        if (node != null) {
            return getTextContent(node)
        } else {
            return null
        }
    }

    fun getFirstChildElement(parentElem: Element?, name: String): Element? {
        if (parentElem == null) {
            return null
        }
        val childs = parentElem.getChildNodes()
        for (i in 0..<childs.getLength()) {
            val node = childs.item(i)
            if (node is Element && name == node.getNodeName()) {
                return node
            }
        }
        return null
    }

    fun getAllChilds(parent: Element?): MutableList<Element?> {
        val r: MutableList<Element?> = LinkedList<Element?>()
        if (parent != null) {
            val childs = parent.getChildNodes()
            for (i in 0..<childs.getLength()) {
                val node = childs.item(i)
                if (node is Element) {
                    r.add(node)
                }
            }
        }
        return r
    }

    class AddDTDFilterInputStream(`in`: InputStream) : FilterInputStream(BufferedInputStream(`in`)) {
        private var count = 0
        private var prefix = DOCTYPE.toByteArray(StandardCharsets.UTF_8)

        init {
            this.`in`.mark(MARK)

            // TODO: we should really find a better solution for this...
            // maybe we could use a FilterReader instead of a FilterInputStream?
            val byte1 = this.`in`.read()
            val byte2 = this.`in`.read()
            val byte3 = this.`in`.read()

            if (byte1 == 239 && byte2 == 187 && byte3 == 191) {
                // skip the UTF-8 BOM
                this.`in`.mark(MARK)
            } else {
                this.`in`.reset()
            }

            var bytesToSkip = 0
            val reader = LineNumberReader(InputStreamReader(this.`in`, StandardCharsets.UTF_8), 100)
            val firstLine = reader.readLine()
            if (firstLine != null) {
                val trimmed = firstLine.trim { it <= ' ' }
                if (trimmed.startsWith("<?xml ")) {
                    val endIndex = trimmed.indexOf("?>")
                    val xmlDecl = trimmed.substring(0, endIndex + 2)
                    prefix = (xmlDecl + "\n" + DOCTYPE).toByteArray(StandardCharsets.UTF_8)
                    bytesToSkip = xmlDecl.toByteArray(StandardCharsets.UTF_8).size
                }
            }

            this.`in`.reset()
            for (i in 0..<bytesToSkip) {
                this.`in`.read()
            }
        }

        @Throws(IOException::class)
        override fun read(): Int {
            if (count < prefix.size) {
                return prefix[count++].toInt()
            }

            return super.read()
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (b == null) {
                throw NullPointerException()
            } else if ((off < 0) || (off > b.size) || (len < 0)
                || ((off + len) > b.size) || ((off + len) < 0)
            ) {
                throw IndexOutOfBoundsException()
            } else if (len == 0) {
                return 0
            }

            var nbrBytesCopied = 0

            if (count < prefix.size) {
                val nbrBytesFromPrefix = min(prefix.size - count, len)
                System.arraycopy(prefix, count, b, off, nbrBytesFromPrefix)
                nbrBytesCopied = nbrBytesFromPrefix
            }

            if (nbrBytesCopied < len) {
                nbrBytesCopied += `in`.read(b, off + nbrBytesCopied, len - nbrBytesCopied)
            }

            count += nbrBytesCopied
            return nbrBytesCopied
        }

        companion object {
            private const val MARK = 10000
            private const val DOCTYPE = "<!DOCTYPE project SYSTEM \"m2-entities.ent\">\n"
        }
    }
}
