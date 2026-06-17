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
package org.gradle.plugin.devel.internal.precompiled

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.groovy.scripts.internal.CompiledScript
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.deprecation.DeprecationLogger.deprecateIndirectUsage
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginsAwareScript
import org.gradle.util.GradleVersion
import org.gradle.util.internal.TextUtil
import java.io.IOException
import java.io.PrintWriter
import java.lang.String
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.text.StringBuilder
import kotlin.text.get
import kotlin.toString

@CacheableTask
abstract class GeneratePluginAdaptersTask : DefaultTask() {
    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations?

    @get:Inject
    protected abstract val classLoaderScopeRegistry: ClassLoaderScopeRegistry?

    @get:Inject
    protected abstract val scriptCompilationHandler: ScriptCompilationHandler?

    @get:Inject
    protected abstract val compileOperationFactory: CompileOperationFactory?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val extractedPluginRequestsClassesDirectory: DirectoryProperty?

    @get:OutputDirectory
    abstract val pluginAdapterSourcesOutputDirectory: DirectoryProperty?

    @get:Internal
    abstract val scriptPlugins: ListProperty<PrecompiledGroovyScript>?

    @TaskAction
    fun generatePluginAdapters() {
        this.fileSystemOperations.delete(Action { spec: DeleteSpec? -> spec!!.delete(this.pluginAdapterSourcesOutputDirectory) })
        this.pluginAdapterSourcesOutputDirectory.get().getAsFile().mkdirs()

        // TODO: Use worker API?
        for (scriptPlugin in this.scriptPlugins.get()) {
            val firstPassCode = generateFirstPassAdapterCode(scriptPlugin)
            generateScriptPluginAdapter(scriptPlugin, firstPassCode)
        }
    }

    private fun generateFirstPassAdapterCode(scriptPlugin: PrecompiledGroovyScript): String {
        val pluginsBlock = loadCompiledPluginsBlocks(scriptPlugin)
        if (!pluginsBlock.getRunDoesSomething()) {
            return ""
        }
        val pluginRequests = extractPluginRequests(pluginsBlock, scriptPlugin)
        validatePluginRequests(scriptPlugin, pluginRequests)

        val applyPlugins = StringBuilder()
        applyPlugins.append("            Class<? extends BasicScript> pluginsBlockClass = Class.forName(\"").append(scriptPlugin.getFirstPassClassName()).append("\").asSubclass(BasicScript.class);\n")
        applyPlugins.append("            BasicScript pluginsBlockScript = pluginsBlockClass.getDeclaredConstructor().newInstance();\n")
        applyPlugins.append("            pluginsBlockScript.setScriptSource(scriptSource(pluginsBlockClass));\n")
        applyPlugins.append("            pluginsBlockScript.init(target, target.getServices());\n")
        applyPlugins.append("            pluginsBlockScript.run();\n")
        for (pluginRequest in pluginRequests) {
            applyPlugins.append("            target.getPluginManager().apply(\"").append(pluginRequest.getId().getId()).append("\");\n")
        }
        return applyPlugins.toString()
    }

    private fun validatePluginRequests(scriptPlugin: PrecompiledGroovyScript, pluginRequests: PluginRequests) {
        val validationErrors: MutableSet<String> = HashSet<String>()
        for (pluginRequest in pluginRequests) {
            if (pluginRequest.getVersion() != null) {
                val advice: String?
                if ("org.gradle.kotlin.kotlin-dsl" == pluginRequest.getId().getId()) {
                    advice = "If you have been using the `kotlin-dsl` helper function, then simply replace it by 'id(\"org.gradle.kotlin.kotlin-dsl\")'"
                } else {
                    advice = "Please remove the version from the offending request"
                }
                validationErrors.add(
                    String.format(
                        "Invalid plugin request %s. " +
                                "Plugin requests from precompiled scripts must not include a version number. " +
                                "%s. Make sure the module containing the " +
                                "requested plugin '%s' is an implementation dependency",
                        pluginRequest, advice, pluginRequest.getId()
                    )
                )
            }
            if (!pluginRequest.isApply()) {
                deprecateIndirectUsage("'apply false' in precompiled script plugins")
                    .withAdvice("Remove 'apply false' from the plugin request for '" + pluginRequest.getId() + "' in '" + projectRelativePathOf(scriptPlugin) + "'.")!!
                    .withContext("'apply false' does not do anything as the plugin will already be added to the classpath when added as a dependency to the precompiled script plugin's build file.")!!
                    .willBecomeAnErrorInGradle10()
                    .withUpgradeGuideSection(9, "deprecate_apply_false_in_precompiled_script_plugins")!!
                    .nagUser()
            }
        }
        if (!validationErrors.isEmpty()) {
            throw LocationAwareException(
                IllegalArgumentException(String.join("\n", validationErrors)),
                scriptPlugin.getBodySource().getResource().getLocation().getDisplayName(),
                pluginRequests.iterator().next().getLineNumber()
            )
        }
    }

    private fun extractPluginRequests(pluginsBlock: CompiledScript<PluginsAwareScript, *>, scriptPlugin: PrecompiledGroovyScript): PluginRequests {
        try {
            val pluginsAwareScript: PluginsAwareScript = pluginsBlock.loadClass().getDeclaredConstructor().newInstance()
            pluginsAwareScript.setScriptSource(scriptPlugin.getBodySource())
            pluginsAwareScript.init(FirstPassPrecompiledScriptRunner(), getServices())
            pluginsAwareScript.run()
            return pluginsAwareScript.getPluginRequests()
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("Could not execute plugins block", e)
        } catch (e: InstantiationException) {
            throw IllegalStateException("Could not execute plugins block", e)
        } catch (e: IllegalAccessException) {
            throw IllegalStateException("Could not execute plugins block", e)
        } catch (e: InvocationTargetException) {
            throw IllegalStateException("Could not execute plugins block", e)
        }
    }

    private fun loadCompiledPluginsBlocks(scriptPlugin: PrecompiledGroovyScript): CompiledScript<PluginsAwareScript, *> {
        val classLoaderScope = this.classLoaderScopeRegistry.getCoreAndPluginsScope()
        val pluginsCompileOperation = this.compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget())
        val compiledPluginRequestsDir = this.extractedPluginRequestsClassesDirectory.get().dir(scriptPlugin.getId()).getAsFile()
        return this.scriptCompilationHandler.loadFromDir(
            scriptPlugin.getFirstPassSource(), scriptPlugin.getContentHash(),
            classLoaderScope, DefaultClassPath.of(compiledPluginRequestsDir), compiledPluginRequestsDir, pluginsCompileOperation, PluginsAwareScript::class.java
        )
    }

    private fun generateScriptPluginAdapter(scriptPlugin: PrecompiledGroovyScript, firstPassCode: kotlin.String) {
        val targetClass = scriptPlugin.getTargetClassName()
        val outputFile = this.pluginAdapterSourcesOutputDirectory.file(scriptPlugin.getPluginAdapterClassName() + ".java").get().getAsFile()

        try {
            PrintWriter(Files.newBufferedWriter(Paths.get(outputFile.toURI()))).use { writer ->
                writer.println("//CHECKSTYLE:OFF")
                writer.println("import org.gradle.util.GradleVersion;")
                writer.println("import org.gradle.groovy.scripts.BasicScript;")
                writer.println("import org.gradle.groovy.scripts.ScriptSource;")
                writer.println("import org.gradle.groovy.scripts.TextResourceScriptSource;")
                writer.println("import org.gradle.internal.resource.StringTextResource;")
                writer.println("/**")
                writer.println(" * Precompiled " + scriptPlugin.getId() + " script plugin.")
                writer.println(" **/")
                writer.println("@SuppressWarnings(\"DefaultPackage\")")
                writer.println("public class " + scriptPlugin.getPluginAdapterClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {")
                writer.println("    private static final String MIN_SUPPORTED_GRADLE_VERSION = \"7.0\";")
                writer.println("    @Override")
                writer.println("    public void apply(" + targetClass + " target) {")
                writer.println("        assertSupportedByCurrentGradleVersion();")
                writer.println("        try {")
                writer.println(firstPassCode)
                writer.println()
                writer.println("            Class<? extends BasicScript> precompiledScriptClass = Class.forName(\"" + scriptPlugin.getBodyClassName() + "\").asSubclass(BasicScript.class);")
                writer.println("            BasicScript script = precompiledScriptClass.getDeclaredConstructor().newInstance();")
                writer.println("            script.setScriptSource(scriptSource(precompiledScriptClass));")
                writer.println("            script.init(target, target.getServices());")
                writer.println("            script.run();")
                writer.println("        } catch (Exception e) {")
                writer.println("            throw new RuntimeException(e);")
                writer.println("        }")
                writer.println("  }")
                writer.println("  private static ScriptSource scriptSource(Class<?> scriptClass) {")
                writer.println("      return new TextResourceScriptSource(new StringTextResource(scriptClass.getSimpleName(), \"\"));")
                writer.println("  }")
                writer.println("  private static void assertSupportedByCurrentGradleVersion() {")
                writer.println("      if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)) < 0) {")
                writer.println("          throw new RuntimeException(\"Precompiled Groovy script plugins built by " + GradleVersion.current() + " require Gradle \"+MIN_SUPPORTED_GRADLE_VERSION+\" or higher\");")
                writer.println("      }")
                writer.println("  }")
                writer.println("}")
                writer.println("//CHECKSTYLE:ON")
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun projectRelativePathOf(scriptPlugin: PrecompiledGroovyScript): kotlin.String {
        val scriptPath = Paths.get(scriptPlugin.getFileName())
        val projectDir = this.projectLayout.getProjectDirectory().getAsFile().toPath()
        return TextUtil.normaliseFileSeparators(projectDir.relativize(scriptPath).toString())
    }
}
