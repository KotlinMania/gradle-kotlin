/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.IConventionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.tasks.bundling.War
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper.afterEvaluateOrExecute
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.EclipseJdt.getSourceCompatibility
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent
import org.gradle.plugins.ide.eclipse.model.EclipseWtpFacet
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.plugins.ide.eclipse.model.internal.WtpClasspathAttributeSupport
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.plugins.ide.internal.IdePluginHelper.withGracefulDegradation
import org.gradle.util.internal.RelativePathUtil
import org.gradle.util.internal.WrapUtil
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A plugin which configures the Eclipse Web Tools Platform.
 *
 * @see [Eclipse plugin reference](https://docs.gradle.org/current/userguide/eclipse_plugin.html)
 */
abstract class EclipseWtpPlugin @Inject constructor(val instantiator: Instantiator?) : IdePlugin() {
    val lifecycleTaskName: String?
        get() = "eclipseWtp"

    override fun shouldDeprecateLifecycleTask(): Boolean {
        return true
    }

    override fun onApply(project: Project) {
        project.getPluginManager().apply(EclipsePlugin::class.java)

        getLifecycleTask().configure(withDescription("Generates Eclipse wtp configuration files."))
        getCleanTask().configure(withDescription("Cleans Eclipse wtp configuration files."))

        project.getTasks().named(EclipsePlugin.Companion.ECLIPSE_TASK_NAME, dependsOn(getLifecycleTask()))
        project.getTasks().named(cleanName(EclipsePlugin.Companion.ECLIPSE_TASK_NAME), dependsOn(getCleanTask()))

        val model = project.getExtensions().getByType<EclipseModel>(EclipseModel::class.java)

        configureEclipseProject(project, model)
        configureEclipseWtpComponent(project, model)
        configureEclipseWtpFacet(project, model)

        // do this after wtp is configured because wtp config is required to update classpath properly
        configureEclipseClasspath(project, model)
    }

    @Suppress("deprecation")
    private fun configureEclipseClasspath(project: Project, model: EclipseModel) {
        project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
            override fun execute(javaPlugin: JavaPlugin?) {
                afterEvaluateOrExecute(project, object : Action<Project?> {
                    override fun execute(project: Project?) {
                        whileDisabled(Runnable {
                            val plusConfigurations = model.getClasspath().plusConfigurations
                            val component = model.getWtp().component
                            plusConfigurations!!.addAll(component!!.rootConfigurations!!)
                            plusConfigurations.addAll(component.libConfigurations!!)
                        })
                    }
                })

                model.getClasspath().file.whenMerged(object : Action<Classpath?> {
                    override fun execute(classpath: Classpath) {
                        WtpClasspathAttributeSupport(project, model).enhance(classpath)
                    }
                })
            }
        })

        project.getPlugins().withType<WarPlugin?>(WarPlugin::class.java, object : Action<WarPlugin?> {
            override fun execute(warPlugin: WarPlugin?) {
                model.getClasspath().containers(WEB_LIBS_CONTAINER)
            }
        })
    }

    @Suppress("deprecation")
    private fun configureEclipseWtpComponent(project: Project, model: EclipseModel) {
        val xmlTransformer = XmlTransformer()
        xmlTransformer.setIndentation("\t")
        val component = whileDisabled<EclipseWtpComponent?>(
            org.gradle.internal.Factory {
                val comp = project.getObjects().newInstance<EclipseWtpComponent>(EclipseWtpComponent::class.java, project, XmlFileContentMerger(xmlTransformer))
                model.getWtp().component = comp
                comp
            })

        val task = project.getTasks().register<GenerateEclipseWtpComponent?>(ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent::class.java, component)
        task.configure(object : Action<GenerateEclipseWtpComponent?> {
            override fun execute(task: GenerateEclipseWtpComponent) {
                task.setDescription("Generates the Eclipse WTP component settings file.")
                task.inputFile = project.file(".settings/org.eclipse.wst.common.component")
                task.outputFile = project.file(".settings/org.eclipse.wst.common.component")
            }
        })
        task.configure(withGracefulDegradation())
        addWorker(task, ECLIPSE_WTP_COMPONENT_TASK_NAME)

        (component as IConventionAware).getConventionMapping().map("deployName", object : Callable<String?> {
            @Throws(Exception::class)
            override fun call(): String? {
                return model.project!!.name
            }
        })

        project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
            override fun execute(javaPlugin: JavaPlugin?) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                val libConfigurations = component.libConfigurations

                libConfigurations!!.add(JavaPluginHelper.getJavaComponent(project).mainFeature.runtimeClasspathConfiguration)
                component.classesDeployPath = "/"
                (component as IConventionAware).getConventionMapping().map("libDeployPath", object : Callable<String?> {
                    @Throws(Exception::class)
                    override fun call(): String {
                        return "../"
                    }
                })
                (component as IConventionAware).getConventionMapping().map("sourceDirs", object : Callable<MutableSet<File?>?> {
                    @Throws(Exception::class)
                    override fun call(): MutableSet<File?> {
                        return getMainSourceDirs(project)
                    }
                })
            }
        })
        project.getPlugins().withType<WarPlugin?>(WarPlugin::class.java, object : Action<WarPlugin?> {
            override fun execute(warPlugin: WarPlugin?) {
                val libConfigurations = component.libConfigurations
                val minusConfigurations = component.minusConfigurations

                libConfigurations!!.add(JavaPluginHelper.getJavaComponent(project).mainFeature.runtimeClasspathConfiguration)
                minusConfigurations!!.add(project.getConfigurations().getByName("providedRuntime"))
                component.classesDeployPath = "/WEB-INF/classes"
                val convention = (component as IConventionAware).getConventionMapping()
                convention.map("libDeployPath", object : Callable<String?> {
                    @Throws(Exception::class)
                    override fun call(): String {
                        return "/WEB-INF/lib"
                    }
                })
                convention.map("contextPath", object : Callable<String?> {
                    @Throws(Exception::class)
                    override fun call(): String? {
                        return (project.getTasks().getByName("war") as War).getArchiveBaseName().getOrNull()
                    }
                })
                convention.map("resources", object : Callable<MutableList<WbResource?>?> {
                    @Throws(Exception::class)
                    override fun call(): MutableList<WbResource?> {
                        val projectDir = project.getProjectDir()
                        val webAppDir = (project.getTasks().getByName("war") as War).webAppDirectory.get().getAsFile()
                        val webAppDirName = RelativePathUtil.relativePath(projectDir, webAppDir)
                        val result: MutableList<WbResource?> = ArrayList<WbResource?>(1)
                        result.add(WbResource("/", webAppDirName))
                        return result
                    }
                })
                convention.map("sourceDirs", object : Callable<MutableSet<File?>?> {
                    @Throws(Exception::class)
                    override fun call(): MutableSet<File?> {
                        return getMainSourceDirs(project)
                    }
                })
            }
        })
        project.getPlugins().withType<EarPlugin?>(EarPlugin::class.java, object : Action<EarPlugin?> {
            override fun execute(earPlugin: EarPlugin?) {
                val libConfigurations = component.libConfigurations
                val rootConfigurations = component.rootConfigurations

                rootConfigurations!!.clear()
                rootConfigurations.add(project.getConfigurations().getByName("deploy"))
                libConfigurations!!.clear()
                libConfigurations.add(project.getConfigurations().getByName("earlib"))
                component.classesDeployPath = "/"
                val convention = (component as IConventionAware).getConventionMapping()
                convention.map("libDeployPath", object : Callable<String?> {
                    @Throws(Exception::class)
                    override fun call(): String {
                        var deployPath = (project.getTasks().findByName(EarPlugin.EAR_TASK_NAME) as Ear).libDirName
                        if (!deployPath!!.startsWith("/")) {
                            deployPath = "/" + deployPath
                        }

                        return deployPath
                    }
                })
                convention.map("sourceDirs", object : Callable<MutableSet<File?>?> {
                    @Throws(Exception::class)
                    override fun call(): MutableSet<File?> {
                        return WrapUtil.toSet<File?>((project.getTasks().findByName(EarPlugin.EAR_TASK_NAME) as Ear).appDirectory.get().getAsFile())
                    }
                })
                project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
                    override fun execute(javaPlugin: JavaPlugin?) {
                        convention.map("sourceDirs", object : Callable<MutableSet<File?>?> {
                            @Throws(Exception::class)
                            override fun call(): MutableSet<File?> {
                                return getMainSourceDirs(project)
                            }
                        })
                    }
                })
            }
        })
    }

    @Suppress("deprecation")
    private fun configureEclipseWtpFacet(project: Project, eclipseModel: EclipseModel) {
        val task = project.getTasks().register<GenerateEclipseWtpFacet?>(
            ECLIPSE_WTP_FACET_TASK_NAME,
            GenerateEclipseWtpFacet::class.java,
            whileDisabled<EclipseWtpFacet?>(org.gradle.internal.Factory { eclipseModel.getWtp().facet })
        )
        task.configure(object : Action<GenerateEclipseWtpFacet?> {
            override fun execute(task: GenerateEclipseWtpFacet) {
                task.setDescription("Generates the Eclipse WTP facet settings file.")
                task.inputFile = project.file(".settings/org.eclipse.wst.common.project.facet.core.xml")
                task.outputFile = project.file(".settings/org.eclipse.wst.common.project.facet.core.xml")
            }
        })
        addWorker(task, ECLIPSE_WTP_FACET_TASK_NAME)

        project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
            override fun execute(javaPlugin: JavaPlugin?) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                (whileDisabled<EclipseWtpFacet?>(org.gradle.internal.Factory { eclipseModel.getWtp().facet }) as IConventionAware).getConventionMapping()
                    .map("facets", object : Callable<MutableList<Facet?>?> {
                        @Throws(Exception::class)
                        override fun call(): MutableList<Facet?> {
                            val result: MutableList<Facet?> = ArrayList<Facet?>(3)
                            result.add(Facet(Facet.FacetType.fixed, "jst.java", null))
                            result.add(Facet(Facet.FacetType.installed, "jst.utility", "1.0"))
                            result.add(
                                Facet(
                                    Facet.FacetType.installed,
                                    "jst.java",
                                    toJavaFacetVersion(project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceCompatibility())
                                )
                            )
                            return result
                        }
                    })
            }
        })
        project.getPlugins().withType<WarPlugin?>(WarPlugin::class.java, object : Action<WarPlugin?> {
            override fun execute(warPlugin: WarPlugin?) {
                (whileDisabled<EclipseWtpFacet?>(org.gradle.internal.Factory { eclipseModel.getWtp().facet }) as IConventionAware).getConventionMapping()
                    .map("facets", object : Callable<MutableList<Facet?>?> {
                        @Throws(Exception::class)
                        override fun call(): MutableList<Facet?> {
                            val result: MutableList<Facet?> = ArrayList<Facet?>(4)
                            result.add(Facet(Facet.FacetType.fixed, "jst.java", null))
                            result.add(Facet(Facet.FacetType.fixed, "jst.web", null))
                            result.add(Facet(Facet.FacetType.installed, "jst.web", "2.4"))
                            result.add(
                                Facet(
                                    Facet.FacetType.installed,
                                    "jst.java",
                                    toJavaFacetVersion(project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceCompatibility())
                                )
                            )
                            return result
                        }
                    })
            }
        })
        project.getPlugins().withType<EarPlugin?>(EarPlugin::class.java, object : Action<EarPlugin?> {
            override fun execute(earPlugin: EarPlugin?) {
                (whileDisabled<EclipseWtpFacet?>(org.gradle.internal.Factory { eclipseModel.getWtp().facet }) as IConventionAware).getConventionMapping()
                    .map("facets", object : Callable<MutableList<Facet?>?> {
                        @Throws(Exception::class)
                        override fun call(): MutableList<Facet?> {
                            val result: MutableList<Facet?> = ArrayList<Facet?>(2)
                            result.add(Facet(Facet.FacetType.fixed, "jst.ear", null))
                            result.add(Facet(Facet.FacetType.installed, "jst.ear", "5.0"))
                            return result
                        }
                    })
            }
        })
    }

    private fun configureEclipseProject(project: Project, model: EclipseModel) {
        val action: Action<Any?> = object : Action<Any?> {
            override fun execute(ignored: Any?) {
                model.project!!.buildCommand("org.eclipse.wst.common.project.facet.core.builder")
                model.project!!.buildCommand("org.eclipse.wst.validation.validationbuilder")
                model.project!!.natures("org.eclipse.wst.common.project.facet.core.nature")
                model.project!!.natures("org.eclipse.wst.common.modulecore.ModuleCoreNature")
                model.project!!.natures("org.eclipse.jem.workbench.JavaEMFNature")
            }
        }
        project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, action)
        project.getPlugins().withType<EarPlugin?>(EarPlugin::class.java, action)
    }

    private fun hasWarOrEarPlugin(project: Project): Boolean {
        return project.getPlugins().hasPlugin(WarPlugin::class.java) || project.getPlugins().hasPlugin(EarPlugin::class.java)
    }

    private fun getMainSourceDirs(project: Project): MutableSet<File?> {
        return project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets().getByName("main").getAllSource().getSrcDirs()
    }

    private fun toJavaFacetVersion(version: JavaVersion): String {
        if (version == JavaVersion.VERSION_1_5) {
            return "5.0"
        }

        if (version == JavaVersion.VERSION_1_6) {
            return "6.0"
        }

        return version.toString()
    }

    companion object {
        const val ECLIPSE_WTP_COMPONENT_TASK_NAME: String = "eclipseWtpComponent"
        const val ECLIPSE_WTP_FACET_TASK_NAME: String = "eclipseWtpFacet"
        const val WEB_LIBS_CONTAINER: String = "org.eclipse.jst.j2ee.internal.web.container"
    }
}
