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
package org.gradle.api.internal.project.antbuilder

import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.groovyloader.GroovySystemLoader
import org.gradle.internal.groovyloader.GroovySystemLoaderFactory
import org.gradle.internal.jvm.Jvm
import java.io.File
import java.util.Vector
import javax.inject.Inject

class DefaultIsolatedAntBuilder : IsolatedAntBuilder, Stoppable {
    private val antLoader: ClassLoader
    private val baseAntLoader: ClassLoader
    private val libClasspath: ClassPath
    private val antAdapterLoader: ClassLoader
    private val classPathRegistry: ClassPathRegistry?
    private val classLoaderFactory: ClassLoaderFactory?
    private val moduleRegistry: ModuleRegistry?
    val classLoaderCache: ClassPathToClassLoaderCache
    private val gradleApiGroovyLoader: GroovySystemLoader
    private val antAdapterGroovyLoader: GroovySystemLoader

    @Inject
    constructor(classPathRegistry: ClassPathRegistry, classLoaderFactory: ClassLoaderFactory, moduleRegistry: ModuleRegistry) {
        this.classPathRegistry = classPathRegistry
        this.classLoaderFactory = classLoaderFactory
        this.moduleRegistry = moduleRegistry
        this.libClasspath = ClassPath.EMPTY
        val groovySystemLoaderFactory = GroovySystemLoaderFactory()
        this.classLoaderCache = ClassPathToClassLoaderCache(groovySystemLoaderFactory)

        val antClasspath: MutableList<File?> = Lists.newArrayList<File?>(classPathRegistry.getClassPath("ANT").getAsFiles())
        // Need tools.jar for compile tasks
        val toolsJar = Jvm.current().getToolsJar()
        if (toolsJar != null) {
            antClasspath.add(toolsJar)
        }

        antLoader = classLoaderFactory.createIsolatedClassLoader("isolated-ant-loader", DefaultClassPath.of(antClasspath))
        val loggingLoaderSpec = FilteringClassLoader.Spec()
        loggingLoaderSpec.allowPackage("org.slf4j")
        loggingLoaderSpec.allowPackage("org.apache.commons.logging")
        loggingLoaderSpec.allowPackage("org.apache.log4j")
        loggingLoaderSpec.allowClass(Logger::class.java)
        loggingLoaderSpec.allowClass(LogLevel::class.java)
        val loggingLoader = FilteringClassLoader(javaClass.getClassLoader(), loggingLoaderSpec)

        this.baseAntLoader = CachingClassLoader(MultiParentClassLoader(antLoader, loggingLoader))

        // Need gradle core to pick up ant logging adapter, AntBuilder and such
        var gradleCoreUrls = moduleRegistry.getModule("gradle-ant-api").getImplementationClasspath()
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-ant").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-core-api").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-core").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-logging-api").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-logging").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-ant").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-datetime").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-groovydoc").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-json").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-templates").getImplementationClasspath())
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("groovy-xml").getImplementationClasspath())

        // Need Transformer (part of AntBuilder API) from base services
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getModule("gradle-base-services").getImplementationClasspath())
        this.antAdapterLoader = VisitableURLClassLoader.fromClassPath("gradle-core-loader", baseAntLoader, gradleCoreUrls)

        gradleApiGroovyLoader = groovySystemLoaderFactory.forClassLoader(this.javaClass.getClassLoader())
        antAdapterGroovyLoader = groovySystemLoaderFactory.forClassLoader(antAdapterLoader)
    }

    protected constructor(copy: DefaultIsolatedAntBuilder, libClasspath: Iterable<File?>?) {
        this.classPathRegistry = copy.classPathRegistry
        this.classLoaderFactory = copy.classLoaderFactory
        this.moduleRegistry = copy.moduleRegistry
        this.antLoader = copy.antLoader
        this.baseAntLoader = copy.baseAntLoader
        this.antAdapterLoader = copy.antAdapterLoader
        this.libClasspath = DefaultClassPath.of(libClasspath)
        this.gradleApiGroovyLoader = copy.gradleApiGroovyLoader
        this.antAdapterGroovyLoader = copy.antAdapterGroovyLoader
        this.classLoaderCache = copy.classLoaderCache
    }

    public override fun withClasspath(classpath: Iterable<File?>?): IsolatedAntBuilder {
        if (LOG!!.isDebugEnabled()) {
            LOG.debug("Forking a new isolated ant builder for classpath : {}", classpath)
        }
        return DefaultIsolatedAntBuilder(this, classpath)
    }

    override fun execute(antBuilderAction: Action<AntBuilderDelegate?>) {
        classLoaderCache.withCachedClassLoader(
            libClasspath, gradleApiGroovyLoader, antAdapterGroovyLoader,
            org.gradle.internal.Factory { VisitableURLClassLoader("ant-lib-loader", baseAntLoader, libClasspath.getAsURLs()) },
            Action { cachedClassLoader: CachedClassLoader? ->
                val classLoader = cachedClassLoader!!.getClassLoader()
                val antBuilder = newInstanceOf("org.gradle.api.internal.project.ant.BasicAntBuilder")
                val antLogger = newInstanceOf("org.gradle.api.internal.project.ant.AntLoggingAdapter")

                // This looks ugly, very ugly, but that is apparently what Ant does itself
                val originalLoader = Thread.currentThread().getContextClassLoader()
                Thread.currentThread().setContextClassLoader(classLoader)
                try {
                    configureAntBuilder(antBuilder, antLogger)

                    // Ideally, we'd delegate directly to the AntBuilder, but its Closure class is different to our caller's
                    // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
                    // because they are not an instanceof its Closure class.
                    antBuilderAction.execute(AntBuilderDelegate(antBuilder, classLoader))
                } finally {
                    Thread.currentThread().setContextClassLoader(originalLoader)
                    disposeBuilder(antBuilder, antLogger)
                }
            })
    }

    private fun newInstanceOf(className: String?): Any {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        try {
            return antAdapterLoader.loadClass(className).getConstructor().newInstance()
        } catch (e: Exception) {
            // should never happen
            throw throwAsUncheckedException(e)
        }
    }

    // We *absolutely* need to avoid polluting the project with ClassInfo from *our* classloader
    // So this class must NOT call any dynamic Groovy code. This means we must do what follows using
    // good old java reflection!
    @Throws(Exception::class)
    private fun getProject(antBuilder: Any): Any {
        return antBuilder.javaClass.getMethod("getProject").invoke(antBuilder)
    }

    protected fun configureAntBuilder(antBuilder: Any, antLogger: Any?) {
        try {
            val project = getProject(antBuilder)
            val projectClass: Class<*> = project.javaClass
            val cl = projectClass.getClassLoader()
            val buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener")
            val addBuildListener = projectClass.getDeclaredMethod("addBuildListener", buildListenerClass)
            val removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass)
            val getBuildListeners = projectClass.getDeclaredMethod("getBuildListeners")
            val listeners = getBuildListeners.invoke(project) as Vector<*>
            removeBuildListener.invoke(project, listeners.get(0))
            addBuildListener.invoke(project, antLogger)
        } catch (ex: Exception) {
            throw throwAsUncheckedException(ex)
        }
    }

    protected fun disposeBuilder(antBuilder: Any, antLogger: Any?) {
        try {
            val project = getProject(antBuilder)
            val projectClass: Class<*> = project.javaClass
            val cl = projectClass.getClassLoader()
            // remove build listener
            val buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener")
            val removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass)
            removeBuildListener.invoke(project, antLogger)
            antBuilder.javaClass.getDeclaredMethod("close").invoke(antBuilder)
        } catch (ex: Exception) {
            throw throwAsUncheckedException(ex)
        }
    }

    override fun stop() {
        classLoaderCache.stop()

        // Remove classes from core Gradle API
        gradleApiGroovyLoader.discardTypesFrom(antAdapterLoader)
        gradleApiGroovyLoader.discardTypesFrom(antLoader)

        // Shutdown the adapter Groovy system
        antAdapterGroovyLoader.shutdown()

        ClassLoaderUtils.tryClose(antAdapterLoader)
    }

    companion object {
        private val LOG = getLogger(DefaultIsolatedAntBuilder::class.java)
    }
}
