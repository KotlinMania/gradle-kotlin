/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import com.google.common.collect.ImmutableList
import com.google.gson.stream.JsonReader
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.io.IOException
import java.io.StringReader

@ServiceScope(Scope.BuildSession::class)
class CommandLineToolVersionLocator(
    private val execActionFactory: ExecActionFactory,
    private val visualCppMetadataProvider: VisualCppMetadataProvider,
    private val vswhereLocator: VswhereVersionLocator
) : AbstractVisualStudioVersionLocator(), VisualStudioVersionLocator {
    override fun locateInstalls(): MutableList<VisualStudioInstallCandidate?> {
        val installs: MutableList<VisualStudioInstallCandidate?> = ArrayList<VisualStudioInstallCandidate?>()

        val vswhereBinary = vswhereLocator.getVswhereInstall()
        if (vswhereBinary != null) {
            val args: MutableList<String?> = ImmutableList.of<String?>("-all", "-legacy", "-format", "json", "-utf8")
            val json = getVswhereOutput(vswhereBinary, args)
            installs.addAll(parseJson(json!!))
        }

        return installs
    }

    override fun getSource(): String {
        return "command line tool"
    }

    private fun getVswhereOutput(vswhereBinary: File, args: MutableList<String?>?): String? {
        val exec = execActionFactory.newExecAction()
        exec!!.args(args)
        exec.executable(vswhereBinary.getAbsolutePath())
        exec.setWorkingDir(vswhereBinary.getParentFile())

        val buffer = StreamByteBuffer()
        exec.setStandardOutput(buffer.outputStream)
        exec.setErrorOutput(NullOutputStream.INSTANCE)
        exec.setIgnoreExitValue(true)
        val result = exec.execute()

        val exitValue = result!!.exitValue
        if (exitValue == 0) {
            return buffer.readAsString("UTF-8")
        } else {
            LOGGER!!.debug("vswhere.exe returned a non-zero exit value ({}) - ignoring", result.exitValue)
            return null
        }
    }

    private fun parseJson(json: String): MutableList<VisualStudioInstallCandidate?> {
        val installs: MutableList<VisualStudioInstallCandidate?> = ArrayList<VisualStudioInstallCandidate?>()
        val reader = JsonReader(StringReader(json))
        try {
            try {
                reader.beginArray()
                while (reader.hasNext()) {
                    val candidate = readInstall(reader)

                    if (candidate != null) {
                        installs.add(candidate)
                    }
                }
                reader.endArray()
            } finally {
                reader.close()
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }

        return installs
    }

    @Throws(IOException::class)
    private fun readInstall(reader: JsonReader): VisualStudioInstallCandidate? {
        var visualStudioInstallPath: String? = null
        var visualStudioVersion: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (key == INSTALLATION_PATH_KEY) {
                visualStudioInstallPath = reader.nextString()
            } else if (key == INSTALLATION_VERSION_KEY) {
                visualStudioVersion = reader.nextString()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        val visualStudioInstallDir = File(visualStudioInstallPath)
        val visualCppMetadata = findVisualCppMetadata(visualStudioInstallDir, visualStudioVersion)

        if (visualCppMetadata == null) {
            LOGGER!!.debug("Ignoring candidate Visual Studio version " + visualStudioVersion + " at " + visualStudioInstallPath + " because it does not appear to be a valid installation")
            return null
        } else {
            return VisualStudioMetadataBuilder()
                .installDir(visualStudioInstallDir)
                .visualCppDir(visualCppMetadata.getVisualCppDir())
                .version(VersionNumber.parse(visualStudioVersion))
                .visualCppVersion(visualCppMetadata.getVersion())
                .build()
        }
    }

    private fun findVisualCppMetadata(installDir: File?, version: String?): VisualCppInstallCandidate? {
        if (VersionNumber.parse(version).getMajor() >= 15) {
            return visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir)
        } else {
            return visualCppMetadataProvider.getVisualCppFromRegistry(version)
        }
    }

    companion object {
        private val LOGGER = getLogger(CommandLineToolVersionLocator::class.java)

        private const val INSTALLATION_PATH_KEY = "installationPath"
        private const val INSTALLATION_VERSION_KEY = "installationVersion"
    }
}
