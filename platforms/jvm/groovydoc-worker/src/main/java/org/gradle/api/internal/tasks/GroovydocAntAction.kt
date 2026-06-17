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
package org.gradle.api.internal.tasks

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.internal.ant.AntWorkAction
import org.gradle.util.internal.VersionNumber
import java.lang.reflect.InvocationTargetException

abstract class GroovydocAntAction : AntWorkAction<GroovydocParameters>() {
    override fun getActionName(): String {
        return "groovydoc"
    }

    public override fun execute(ant: AntBuilderDelegate) {
        val parameters = getParameters()

        val version: VersionNumber = groovyVersion

        val args: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        args.put("sourcepath", parameters.getTmpDir().get().getAsFile())
        args.put("destdir", parameters.getDestinationDirectory().get().getAsFile())
        args.put("use", parameters.getUse().get())
        if (isAtLeast(version, "2.4.6")) {
            args.put("noTimestamp", parameters.getNoTimestamp().get())
            args.put("noVersionStamp", parameters.getNoVersionStamp().get())
        }
        args.put(parameters.getAccess().get().name.lowercase(), true)

        args.put("author", parameters.getIncludeAuthor().get())
        if (isAtLeast(version, "1.7.3")) {
            args.put("processScripts", parameters.getProcessScripts().get())
            args.put("includeMainForScripts", parameters.getIncludeMainForScripts().get())
        }
        putIfNotNull(args, "windowtitle", parameters.getWindowTitle().getOrNull())
        putIfNotNull(args, "doctitle", parameters.getDocTitle().getOrNull())
        putIfNotNull(args, "header", parameters.getHeader().getOrNull())
        putIfNotNull(args, "footer", parameters.getFooter().getOrNull())
        putIfNotNull(args, "overview", parameters.getOverview().getOrNull())

        ant.taskdef("groovydoc", "org.codehaus.groovy.ant.Groovydoc")

        ant.createNode("groovydoc", args, Runnable {
            for (link in parameters.getLinks().get()) {
                ant.createNode(
                    "link",
                    ImmutableMap.of<String?, Any?>(
                        "packages", Joiner.on(",").join(link.getPackages()),
                        "href", link.getUrl()
                    )
                )
            }
        })
    }

    companion object {
        private val groovyVersion: VersionNumber
            get() {
                try {
                    val groovySystem = Thread.currentThread().getContextClassLoader().loadClass("groovy.lang.GroovySystem")
                    val getVersion = groovySystem.getDeclaredMethod("getVersion")
                    val versionString = getVersion.invoke(null) as String?
                    return VersionNumber.parse(versionString)
                } catch (ex: NoSuchMethodException) {
                    // ignore
                } catch (ex: ClassNotFoundException) {
                } catch (ex: IllegalAccessException) {
                } catch (ex: InvocationTargetException) {
                }
                return VersionNumber.UNKNOWN
            }

        private fun isAtLeast(version: VersionNumber, versionString: String?): Boolean {
            return version.compareTo(VersionNumber.parse(versionString)) >= 0
        }

        private fun putIfNotNull(map: MutableMap<String?, Any?>, key: String?, value: Any?) {
            if (value != null) {
                map.put(key, value)
            }
        }
    }
}
