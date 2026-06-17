/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.launcher.cli.internal

import org.apache.tools.ant.Main
import org.codehaus.groovy.util.ReleaseInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.internal.DefaultGradleVersion
import org.gradle.util.internal.KotlinDslVersion
import org.jspecify.annotations.NullMarked
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Renders the output of `--version`.
 */
@NullMarked
object VersionInfoRenderer {
    @JvmStatic
    fun render(daemonJvm: String): String {
        return VersionInfoRenderer.render(daemonJvm, null)
    }

    @JvmStatic
    fun renderWithLauncherJvm(daemonJvm: String): String {
        return render(daemonJvm, Jvm.current().toString())
    }

    private fun render(daemonJvm: String, launcherJvm: String): String {
        val currentVersion = DefaultGradleVersion.current()
        val sw = StringWriter()
        val out = PrintWriter(sw)
        out.println()
        out.println("------------------------------------------------------------")
        out.println("Gradle " + currentVersion.getVersion())
        out.println("------------------------------------------------------------")
        out.println()
        val maxKey = "Launcher JVM".length
        VersionInfoRenderer.printAligned(out, "Build time", currentVersion.getBuildTimestamp()!!, maxKey)
        VersionInfoRenderer.printAligned(out, "Revision", currentVersion.getGitRevision()!!, maxKey)
        out.println()
        printAligned(out, "Kotlin", KotlinDslVersion.current().getKotlinVersion(), maxKey)
        printAligned(out, "Groovy", ReleaseInfo.getVersion(), maxKey)
        printAligned(out, "Ant", Main.getAntVersion(), maxKey)
        if (launcherJvm != null) {
            printAligned(out, "Launcher JVM", launcherJvm, maxKey)
        }
        printAligned(out, "Daemon JVM", daemonJvm, maxKey)
        printAligned(out, "OS", OperatingSystem.current().toString(), maxKey)
        out.println()
        out.flush()
        return sw.toString()
    }

    private fun printAligned(out: PrintWriter, key: String, value: String, maxKey: Int) {
        out.print(key + ": ")
        for (i in key.length..<maxKey + 1) {
            out.print(' ')
        }
        out.println(value)
    }
}
