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
package org.gradle.api.internal.plugins

import com.google.common.base.Function
import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import com.google.common.collect.Streams
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Transformer
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails
import org.gradle.util.internal.CollectionUtils.toStringList
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.stream.Collectors

class StartScriptTemplateBindingFactory private constructor(private val windows: Boolean) : Transformer<MutableMap<String?, String?>?, JavaAppStartScriptGenerationDetails?> {
    override fun transform(details: JavaAppStartScriptGenerationDetails): MutableMap<String?, String?> {
        val binding: MutableMap<String?, String?> = HashMap<String?, String?>()
        // Before changing, see the note in ScriptBindingParameter's Javadoc
        binding.put(ScriptBindingParameter.APP_NAME.key, details.getApplicationName())
        binding.put(ScriptBindingParameter.GIT_REF.key, details.getGitRef())
        binding.put(ScriptBindingParameter.OPTS_ENV_VAR.key, details.getOptsEnvironmentVar())
        binding.put(ScriptBindingParameter.EXIT_ENV_VAR.key, details.getExitEnvironmentVar())

        val entryPoint: AppEntryPoint? = getEntryPoint(details)
        val entryPointArgs: String? = encodeEntryPoint(entryPoint)
        binding.put(ScriptBindingParameter.ENTRY_POINT_ARGS.key, entryPointArgs)
        binding.put(ScriptBindingParameter.MAIN_CLASS_NAME.key, getMainClassName(entryPoint, entryPointArgs))
        binding.put(ScriptBindingParameter.MODULE_ENTRY_POINT.key, if (entryPoint is MainModule) getModuleEntryPoint(entryPoint) else null)

        binding.put(ScriptBindingParameter.DEFAULT_JVM_OPTS.key, createJoinedDefaultJvmOpts(details.getDefaultJvmOpts()))
        binding.put(ScriptBindingParameter.APP_NAME_SYS_PROP.key, details.getAppNameSystemProperty())
        binding.put(ScriptBindingParameter.APP_HOME_REL_PATH.key, createJoinedAppHomeRelativePath(details.getScriptRelPath()))
        binding.put(ScriptBindingParameter.CLASSPATH.key, createJoinedPath(details.getClasspath()))
        binding.put(ScriptBindingParameter.MODULE_PATH.key, createJoinedPath(details.getModulePath()))
        return binding
    }

    private fun encodeEntryPoint(entryPoint: AppEntryPoint?): String? {
        if (entryPoint is MainClass) {
            return entryPoint.getMainClassName()
        } else if (entryPoint is MainModule) {
            return "--module " + getModuleEntryPoint(entryPoint)
        } else if (entryPoint is ExecutableJar) {
            // We need to also escape quotes in the JAR path, so our quotes aren't broken by the JAR path
            // In theory we should be doing this in `encodePath`, but I wanted to avoid making behavioral changes to `classpath` and `modulePath`
            val jarPathEscaped = encodePath(entryPoint.getJarPath()).replace("\"", "\\\"")
            return "-jar \"" + jarPathEscaped + "\""
        } else {
            throw IllegalArgumentException("Unknown entry point type: " + entryPoint)
        }
    }

    private fun createJoinedPath(path: Iterable<String?>): String {
        return Streams.stream<String?>(path).map<String?> { path: String? -> this.encodePath(path!!) }.collect(Collectors.joining(this.multiPathSeparator))
    }

    private fun encodePath(path: String): String {
        if (windows) {
            return "%APP_HOME%\\" + path.replace("/", "\\")
        } else {
            return "\$APP_HOME/" + path.replace("\\", "/")
        }
    }

    private fun createJoinedDefaultJvmOpts(defaultJvmOpts: Iterable<String?>?): String {
        if (windows) {
            if (defaultJvmOpts == null) {
                return ""
            }

            val quotedDefaultJvmOpts = Iterables.transform<String?, String?>(toStringList(defaultJvmOpts), object : Function<String?, String?> {
                override fun apply(jvmOpt: String): String? {
                    return "\"" + escapeWindowsJvmOpt(jvmOpt) + "\""
                }
            })

            val spaceJoiner = Joiner.on(" ")
            return spaceJoiner.join(quotedDefaultJvmOpts)
        } else {
            if (defaultJvmOpts == null) {
                return ""
            }

            val quotedDefaultJvmOpts = Iterables.transform<String?, String?>(toStringList(defaultJvmOpts), object : Function<String?, String?> {
                override fun apply(jvmOpt: String): String? {
                    //quote ', ", \, $. Probably not perfect. TODO: identify non-working cases, fail-fast on them
                    var jvmOpt = jvmOpt
                    jvmOpt = jvmOpt.replace("\\", "\\\\")
                    jvmOpt = jvmOpt.replace("\"", "\\\"")
                    jvmOpt = jvmOpt.replace("'", "'\"'\"'")
                    jvmOpt = jvmOpt.replace("`", "'\"`\"'")
                    jvmOpt = jvmOpt.replace("$", "\\$")
                    return "\"" + jvmOpt + "\""
                }
            })

            //put the whole arguments string in single quotes, unless defaultJvmOpts was empty,
            // in which case we output "" to stay compatible with existing builds that scan the script for it
            val spaceJoiner = Joiner.on(" ")
            if (Iterables.size(quotedDefaultJvmOpts) > 0) {
                return "'" + spaceJoiner.join(quotedDefaultJvmOpts) + "'"
            }

            return "\"\""
        }
    }

    private fun escapeWindowsJvmOpt(jvmOpts: String): String {
        var wasOnBackslash = false
        val escapedJvmOpt = StringBuilder()
        val it: CharacterIterator = StringCharacterIterator(jvmOpts)

        //argument quoting:
        // - " must be encoded as \"
        // - % must be encoded as %%
        // - pathological case: \" must be encoded as \\\", but other than that, \ MUST NOT be quoted
        // - other characters (including ') will not be quoted
        // - use a state machine rather than regexps
        var ch = it.first()
        while (ch != CharacterIterator.DONE) {
            var repl: String? = ch.toString()

            if (ch == '%') {
                repl = "%%"
            } else if (ch == '"') {
                repl = (if (wasOnBackslash) '\\' else "").toString() + "\\\""
            }
            wasOnBackslash = ch == '\\'
            escapedJvmOpt.append(repl)
            ch = it.next()
        }

        return escapedJvmOpt.toString()
    }

    /**
     * @implNote These names and their behavior are public API, documented in [org.gradle.jvm.application.tasks.CreateStartScripts].
     * Changes to these names or their behavior must be made carefully to avoid breaking existing custom script templates. Please update the documentation if you change them.
     */
    private enum class ScriptBindingParameter(key: String) {
        APP_NAME("applicationName"),
        GIT_REF("gitRef"),
        OPTS_ENV_VAR("optsEnvironmentVar"),
        EXIT_ENV_VAR("exitEnvironmentVar"),
        MODULE_ENTRY_POINT("moduleEntryPoint"),
        MAIN_CLASS_NAME("mainClassName"),
        ENTRY_POINT_ARGS("entryPointArgs"),
        DEFAULT_JVM_OPTS("defaultJvmOpts"),
        APP_NAME_SYS_PROP("appNameSystemProperty"),
        APP_HOME_REL_PATH("appHomeRelativePath"),
        CLASSPATH("classpath"),
        MODULE_PATH("modulePath");

        val key: String?

        init {
            this.key = key
        }
    }

    fun createJoinedAppHomeRelativePath(scriptRelPath: String?): String {
        val depth = StringUtils.countMatches(scriptRelPath, "/")
        if (depth == 0) {
            return ""
        }

        val appHomeRelativePath: MutableList<String?> = ArrayList<String?>(depth)
        for (i in 0..<depth) {
            appHomeRelativePath.add("..")
        }

        return Joiner.on(this.pathElementSeparator).join(appHomeRelativePath)
    }

    private val pathElementSeparator: String
        /**
         * The separator used to separate each element in a path.
         *
         * @return the path element separator
         */
        get() = if (windows) "\\" else "/"

    private val multiPathSeparator: String
        /**
         * The separator used to separate each path in a multi-path argument.
         *
         * @return the multi-path separator
         */
        get() = if (windows) ";" else ":"

    companion object {
        fun windows(): StartScriptTemplateBindingFactory {
            return StartScriptTemplateBindingFactory(true)
        }

        fun unix(): StartScriptTemplateBindingFactory {
            return StartScriptTemplateBindingFactory(false)
        }

        private fun getMainClassName(entryPoint: AppEntryPoint?, entryPointArgs: String?): String? {
            if (entryPoint is MainClass) {
                return entryPoint.getMainClassName()
            } else if (entryPoint is MainModule) {
                // For legacy reasons, keep the mainClassName as the module invocation for scripts which used it that way
                return entryPointArgs
            } else {
                return ""
            }
        }

        private fun getEntryPoint(details: JavaAppStartScriptGenerationDetails): AppEntryPoint? {
            if (details is DefaultJavaAppStartScriptGenerationDetails) {
                return details.getEntryPoint()
            } else {
                // Provide compatibility in case someone was manually implementing JavaAppStartScriptGenerationDetails
                return MainClass(details.getMainClassName())
            }
        }

        private fun getModuleEntryPoint(entryPoint: MainModule): String {
            val mainClassName = entryPoint.getMainClassName()
            val hasMainClass = mainClassName != null
            return entryPoint.getMainModuleName() + (if (hasMainClass) "/" + mainClassName else "")
        }
    }
}
