/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import com.google.common.annotations.VisibleForTesting
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

open class DefaultOsXJavaHomeCommand(private val execHandleFactory: ClientExecHandleBuilderFactory) : OsXJavaHomeCommand {
    override fun findJavaHomes(): MutableSet<File?>? {
        try {
            val output = executeJavaHome()
            return parse(output)
        } catch (e: ProcessExecutionException) {
            val errorMessage = "Java Toolchain auto-detection failed to find local MacOS system JVMs"
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(errorMessage, e)
            } else {
                LOGGER.info(errorMessage)
            }
        }
        return mutableSetOf<File?>()
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun executeJavaHome(): Reader {
        val outputStream = ByteArrayOutputStream()
        executeCommand(outputStream)
        return InputStreamReader(ByteArrayInputStream(outputStream.toByteArray()))
    }

    @VisibleForTesting
    protected open fun executeCommand(outputStream: ByteArrayOutputStream) {
        val execHandleBuilder = execHandleFactory.newExecHandleBuilder()
        execHandleBuilder!!.setWorkingDir(File(".").getAbsoluteFile())
        execHandleBuilder.commandLine("/usr/libexec/java_home", "-V")
        execHandleBuilder.environment!!.remove("JAVA_VERSION") //JAVA_VERSION filters the content of java_home's output
        execHandleBuilder.setErrorOutput(outputStream) // verbose output is written to stderr
        execHandleBuilder.setStandardOutput(ByteArrayOutputStream())
        execHandleBuilder.build()!!.start()!!.waitForFinish()!!.assertNormalExitValue()
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultOsXJavaHomeCommand::class.java)

        private val INSTALLATION_PATTERN: Pattern = Pattern.compile(".+\\s+(/.+)")

        @VisibleForTesting
        fun parse(output: Reader): MutableSet<File?> {
            val reader = BufferedReader(output)
            return reader.lines().flatMap<String?> { line: String? ->
                val matcher: Matcher = INSTALLATION_PATTERN.matcher(line)
                if (matcher.matches()) {
                    val javaHome = matcher.group(1)
                    return@flatMap Stream.of<String?>(javaHome)
                }
                Stream.empty<String?>()
            }.map<File?> { pathname: String? -> File(pathname) }.collect(Collectors.toSet())
        }
    }
}
