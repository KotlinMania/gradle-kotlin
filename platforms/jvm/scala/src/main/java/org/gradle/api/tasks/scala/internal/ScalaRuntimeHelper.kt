/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.tasks.scala.internal

import java.io.File
import java.util.regex.Pattern

object ScalaRuntimeHelper {
    private val SCALA_JAR_PATTERN: Pattern = Pattern.compile("scala3?-(\\w.*?)-(\\d.*).jar")

    /**
     * Searches the specified class path for a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.) with the specified appendix (compiler, library, jdbc, etc.).
     * If no such file is found, `null` is returned.
     *
     * @param classpath the class path to search
     * @param appendix the appendix to search for
     * @return a Scala Jar file with the specified appendix
     */
    fun findScalaJar(classpath: Iterable<File>, appendix: String?): File? {
        for (file in classpath) {
            val matcher = SCALA_JAR_PATTERN.matcher(file.getName())
            if (matcher.matches() && matcher.group(1) == appendix) {
                return file
            }
        }
        return null
    }

    /**
     * Determines the version of a Scala Jar file (scala-compiler, scala-library,
     * scala-jdbc, etc.). If the version cannot be determined, or the file is not a Scala
     * Jar file, `null` is returned.
     *
     *
     * Implementation note: The version is determined by parsing the file name, which
     * is expected to match the pattern 'scala-[component]-[version].jar'.
     *
     * @param scalaJar a Scala Jar file
     * @return the version of the Scala Jar file
     */
    fun getScalaVersion(scalaJar: File): String? {
        val matcher = SCALA_JAR_PATTERN.matcher(scalaJar.getName())
        return if (matcher.matches()) matcher.group(2) else null
    }

    /**
     * Determines if the Scala version is of the 3.x line.
     *
     * @param scalaVersion the version to test
     * @return `true` if this version starts with `3.`, `false` otherwise
     */
    fun isScala3(scalaVersion: String): Boolean {
        return scalaVersion.startsWith("3.")
    }
}
