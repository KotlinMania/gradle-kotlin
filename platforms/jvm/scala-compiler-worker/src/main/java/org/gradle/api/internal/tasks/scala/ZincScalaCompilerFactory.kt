/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import com.google.common.collect.Iterables
import org.gradle.api.logging.Logging.getLogger
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.time.Time.startTimer
import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.RawCompiler
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.ZincUtil
import sbt.internal.inc.classpath.ClassLoaderCache
import scala.Function1
import scala.Option
import scala.collection.JavaConverters
import scala.collection.immutable.Seq
import scala.runtime.BoxedUnit
import xsbti.ArtifactInfo
import xsbti.Reporter
import xsbti.compile.ClasspathOptionsUtil
import xsbti.compile.ScalaCompiler
import xsbti.compile.ZincCompilerUtil
import java.io.File
import java.io.IOException
import java.lang.String
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Arrays
import java.util.Properties
import java.util.concurrent.Callable
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.RuntimeException
import kotlin.Suppress
import kotlin.Throws
import kotlin.arrayOf
import kotlin.text.contains
import kotlin.text.format
import kotlin.text.startsWith

@Suppress("deprecation")
object ZincScalaCompilerFactory {
    private val LOGGER = getLogger(ZincScalaCompilerFactory::class.java)
    private const val CLASSLOADER_CACHE_SIZE = 4
    private const val COMPILER_CLASSLOADER_CACHE_SIZE = 4
    private const val SCALA_3_COMPILER_ID = "scala3-compiler_3"
    private const val SCALA_3_LIBRARY_ID = "scala3-library_3"
    private val CLASSLOADER_CACHE = GuavaBackedClassLoaderCache<HashCode?>(CLASSLOADER_CACHE_SIZE)
    private val COMPILER_CLASSLOADER_CACHE: ClassLoaderCache

    init {
        // Load TimeCheckingClassLoaderCache and use it to create cache via reflection
        // If we detect that we are using zinc 1.2.x, we fallback to default cache
        var abstractCacheClass: Class<*>?
        var checkingClass: Class<*>?
        try {
            abstractCacheClass = ZincScalaCompilerFactory::class.java.getClassLoader().loadClass("sbt.internal.inc.classpath.AbstractClassLoaderCache")
            checkingClass = ZincScalaCompilerFactory::class.java.getClassLoader().loadClass("org.gradle.api.internal.tasks.scala.TimeCheckingClassLoaderCache")
        } catch (ex: ClassNotFoundException) {
            abstractCacheClass = null
            checkingClass = null
        }
        if (checkingClass != null) {
            try {
                val constructor = ClassLoaderCache::class.java.getConstructor(abstractCacheClass)
                val cache: Any = checkingClass.getConstructors()[0].newInstance(COMPILER_CLASSLOADER_CACHE_SIZE)
                COMPILER_CLASSLOADER_CACHE = constructor.newInstance(cache)
            } catch (e: Exception) {
                throw RuntimeException("Failed to instantiate ClassLoaderCache", e)
            }
        } else {
            COMPILER_CLASSLOADER_CACHE = ClassLoaderCache(URLClassLoader(arrayOf<URL?>()))
        }
    }

    fun getCompiler(globalScopedCacheBuilderFactory: GlobalScopedCacheBuilderFactory, hashedScalaClasspath: HashedClasspath): ZincScalaCompiler {
        val scalaInstance: ScalaInstance?
        try {
            scalaInstance = getScalaInstance(hashedScalaClasspath)
        } catch (e: Exception) {
            throw RuntimeException("Failed create instance of the scala compiler", e)
        }

        val zincVersion = ZincCompilerUtil::class.java.getPackage().getImplementationVersion()
        val scalaVersion = scalaInstance.actualVersion()

        val javaVersion = Jvm.current().getJavaVersion()!!.majorVersion
        val zincCacheKey = String.format("zinc-%s_%s_%s", zincVersion, scalaVersion, javaVersion)
        val zincCacheName = String.format("%s compiler cache", zincCacheKey)
        val zincCache = globalScopedCacheBuilderFactory.createCacheBuilder(zincCacheKey)
            .withDisplayName(zincCacheName)
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()


        val compilerBridgeJar: File?
        if (isScala3(scalaVersion)) {
            compilerBridgeJar = findFile("scala3-sbt-bridge", hashedScalaClasspath.getClasspath())
        } else {
            val compilerBridgeSourceJar = findFile("compiler-bridge", hashedScalaClasspath.getClasspath())
            compilerBridgeJar = getBridgeJar(zincCache, scalaInstance, compilerBridgeSourceJar, sbt.util.Logger.xlog2Log(SbtLoggerAdapter()))
        }

        val scalaCompiler: ScalaCompiler = AnalyzingCompiler(
            scalaInstance,
            ZincUtil.constantBridgeProvider(scalaInstance, compilerBridgeJar),
            ClasspathOptionsUtil.manual(),
            Function1 { k: Seq<String?>? -> BoxedUnit.UNIT },
            Option.apply<ClassLoaderCache?>(COMPILER_CLASSLOADER_CACHE)
        )

        return ZincScalaCompiler(scalaInstance, scalaCompiler, AnalysisStoreProvider())
    }

    private fun getClassLoader(classpath: ClassPath, parent: ClassLoader?): ClassLoader {
        try {
            val urls: MutableList<URL?> = ArrayList<URL?>()
            for (file in classpath.getAsFiles()) {
                // Having the bridge in the classloader breaks zinc
                if (!file.toString().contains("scala3-sbt-bridge")) {
                    urls.add(file.toURI().toURL())
                }
            }
            if (parent != null) {
                return URLClassLoader(urls.toTypedArray<URL?>(), parent)
            } else {
                return URLClassLoader(urls.toTypedArray<URL?>())
            }
        } catch (ee: Exception) {
            throw RuntimeException(ee)
        }
    }

    private fun isScala3(version: String): Boolean {
        return version.startsWith("3.")
    }

    private fun getCachedClassLoader(classpath: HashedClasspath, parent: ClassLoader?): ClassLoader {
        try {
            return CLASSLOADER_CACHE.get(classpath.getHash(), object : Callable<ClassLoader?> {
                @Throws(Exception::class)
                override fun call(): ClassLoader {
                    return getClassLoader(classpath.getClasspath(), parent)
                }
            })
        } catch (ee: Exception) {
            throw RuntimeException(ee)
        }
    }

    @Throws(MalformedURLException::class)
    private fun getScalaInstance(hashedScalaClasspath: HashedClasspath): ScalaInstance {
        val scalaClasspath = hashedScalaClasspath.getClasspath()
        val libraryJar = findFile(ArtifactInfo.ScalaLibraryID, scalaClasspath)
        var libraryUrls: Array<URL?>?
        var isScala3 = false
        try {
            val library3Jar = findFile(SCALA_3_LIBRARY_ID, scalaClasspath)
            isScala3 = true
            libraryUrls = arrayOf<URL>(library3Jar.toURI().toURL(), libraryJar.toURI().toURL())
        } catch (e: IllegalStateException) {
            libraryUrls = arrayOf<URL>(libraryJar.toURI().toURL())
        }
        val scalaLibraryClassLoader: ClassLoader?
        val scalaClassLoader: ClassLoader
        if (isScala3) {
            scalaLibraryClassLoader = ScalaCompilerLoader(libraryUrls, Reporter::class.java.getClassLoader())
            scalaClassLoader = getCachedClassLoader(hashedScalaClasspath, scalaLibraryClassLoader)
        } else {
            scalaLibraryClassLoader = getClassLoader(DefaultClassPath.of(libraryJar), null)
            scalaClassLoader = getCachedClassLoader(hashedScalaClasspath, null)
        }
        val scalaVersion = getScalaVersion(scalaClassLoader)

        val compilerJar: File?
        if (isScala3) {
            compilerJar = findFile(SCALA_3_COMPILER_ID, scalaClasspath)
        } else {
            compilerJar = findFile(ArtifactInfo.ScalaCompilerID, scalaClasspath)
        }

        return ScalaInstance(
            scalaVersion,
            scalaClassLoader,
            scalaLibraryClassLoader,
            libraryJar,
            compilerJar,
            Iterables.toArray<File?>(scalaClasspath.getAsFiles(), File::class.java),
            Option.empty<String?>()
        )
    }

    private fun getBridgeJar(zincCache: PersistentCache, scalaInstance: ScalaInstance, compilerBridgeSourceJar: File, logger: sbt.util.Logger?): File {
        return zincCache.useCache<File>(Supplier {
            val bridgeJar = File(zincCache.getBaseDir(), "compiler-bridge.jar")
            if (bridgeJar.exists()) {
                // compiler interface exists, use it
                return@useCache bridgeJar
            } else {
                // generate from sources jar
                val timer = startTimer()
                val rawCompiler = RawCompiler(scalaInstance, ClasspathOptionsUtil.manual(), logger)
                val sourceJars = JavaConverters.collectionAsScalaIterable<Path?>(mutableListOf<Path?>(compilerBridgeSourceJar.toPath()))
                val xsbtiJarsAsPath = Arrays.stream<File?>(scalaInstance.allJars()).map<Path?> { obj: File? -> obj!!.toPath() }.collect(Collectors.toList())
                val xsbtiJars = JavaConverters.collectionAsScalaIterable<Path?>(xsbtiJarsAsPath)
                `AnalyzingCompiler$`.`MODULE$`.compileSources(sourceJars, bridgeJar.toPath(), xsbtiJars, "compiler-bridge", rawCompiler, logger)

                val interfaceCompletedMessage = String.format("Scala Compiler interface compilation took %s", timer.elapsed)
                if (timer.elapsedMillis > 30000) {
                    LOGGER!!.info(interfaceCompletedMessage)
                } else {
                    LOGGER!!.debug(interfaceCompletedMessage)
                }

                return@useCache bridgeJar
            }
        })
    }

    private fun findFile(prefix: kotlin.String, classpath: ClassPath): File {
        for (f in classpath.getAsFiles()) {
            if (f.getName().startsWith(prefix)) {
                return f
            }
        }
        throw IllegalStateException(kotlin.String.format("Cannot find any files starting with %s in %s", prefix, classpath.getAsFiles()))
    }

    private fun getScalaVersion(scalaClassLoader: ClassLoader): kotlin.String {
        try {
            val props = Properties()
            props.load(scalaClassLoader.getResourceAsStream("compiler.properties"))
            return props.getProperty("version.number")
        } catch (e: IOException) {
            throw IllegalStateException("Unable to determine scala version")
        }
    }
}
