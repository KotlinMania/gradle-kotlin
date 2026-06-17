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
package org.gradle.plugins.ear

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.EarModule
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor
import org.gradle.plugins.ear.descriptor.internal.DefaultEarModule
import org.gradle.plugins.ear.descriptor.internal.DefaultEarWebModule
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.GUtil
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

/**
 * Assembles an EAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class Ear : Jar() {
    /**
     * The name of the library directory in the EAR file. Default is "lib".
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var libDirName: String? = null

    /**
     * The deployment descriptor configuration.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var deploymentDescriptor: DeploymentDescriptor? = null
    private val lib: CopySpec

    init {
        getArchiveExtension().set(EAR_EXTENSION)
        setMetadataCharset("UTF-8")
        this.generateDeploymentDescriptor.convention(true)
        lib = getRootSpec().addChildBeforeSpec(getMainSpec()).into(
            SerializableLambdas.callable<String?>(SerializableLambdas.SerializableCallable {
                GUtil.elvis<String?>(
                    this.libDirName, EarPlugin.Companion.DEFAULT_LIB_DIR_NAME
                )
            })
        )
        getMainSpec().appendCachingSafeCopyAction(SerializableLambdas.action<FileCopyDetails?>(SerializableLambdas.SerializableAction { details: FileCopyDetails? ->
            if (this.generateDeploymentDescriptor.get()) {
                checkIfShouldGenerateDeploymentDescriptor(details!!)
                recordTopLevelModules(details)
            }
        }))

        // create our own metaInf which runs after mainSpec's files
        // this allows us to generate the deployment descriptor after recording all modules it contains
        val metaInf = getMainSpec().addChild().into("META-INF") as CopySpecInternal
        val descriptorChild = metaInf.addChild()
        // the generated descriptor should only be used if one does not already exist
        descriptorChild.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
        descriptorChild.from(SerializableLambdas.callable<FileTreeInternal?>(SerializableLambdas.SerializableCallable {
            val descriptor = this.deploymentDescriptor
            if (descriptor != null && this.generateDeploymentDescriptor.get()) {
                if (descriptor.getLibraryDirectory() == null) {
                    descriptor.setLibraryDirectory(this.libDirName)
                }

                val descriptorFileName = descriptor.getFileName()
                if (descriptorFileName.contains("/") || descriptorFileName.contains(File.separator)) {
                    throw InvalidUserDataException("Deployment descriptor file name must be a simple name but was " + descriptorFileName)
                }

                // TODO: Consider capturing the `descriptor` as a spec
                //  so any captured manifest attribute providers are re-evaluated
                //  on each run.
                //  See https://github.com/gradle/configuration-cache/issues/168
                val outputChangeListener: OutputChangeListener = outputChangeListener()
                return@callable fileCollectionFactory()!!.generated(
                    getTemporaryDirFactory(),
                    descriptorFileName,
                    SerializableLambdas.action<File?>(SerializableLambdas.SerializableAction { file: File? -> outputChangeListener.invalidateCachesFor(mutableSetOf<String?>(file!!.getAbsolutePath())) }),
                    SerializableLambdas.action<OutputStream?>(SerializableLambdas.SerializableAction { outputStream: OutputStream? ->  // delay obtaining contents to account for descriptor changes
                        // (for instance, due to modules discovered)
                        descriptor.writeTo(OutputStreamWriter(outputStream))
                    }
                    )
                )
            }
            null
        }))
    }

    private fun fileCollectionFactory(): FileCollectionFactory? {
        return getServices().get<FileCollectionFactory?>(FileCollectionFactory::class.java)
    }

    private fun outputChangeListener(): OutputChangeListener {
        return getServices().get<OutputChangeListener?>(OutputChangeListener::class.java)!!
    }

    private fun recordTopLevelModules(details: FileCopyDetails) {
        val deploymentDescriptor = this.deploymentDescriptor
        // since we might generate the deployment descriptor, record each top-level module
        if (deploymentDescriptor != null && details.getPath().lastIndexOf("/") <= 0) {
            val module: EarModule?
            if (details.getPath().lowercase().endsWith(".war")) {
                module = DefaultEarWebModule(details.getPath(), details.getPath().substring(0, details.getPath().lastIndexOf(".")))
            } else {
                module = DefaultEarModule(details.getPath())
            }

            deploymentDescriptor.getModules().add(module)
        }
    }

    private fun checkIfShouldGenerateDeploymentDescriptor(details: FileCopyDetails) {
        val deploymentDescriptor = this.deploymentDescriptor
        val descriptorPath = if (deploymentDescriptor != null) "META-INF/" + deploymentDescriptor.getFileName() else null
        if (details.getPath().equals(descriptorPath, ignoreCase = true)) {
            // the deployment descriptor already exists; no need to generate it
            this.deploymentDescriptor = null
            details.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
        }
    }

    @Inject
    abstract override fun getObjectFactory(): ObjectFactory

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     *
     * The given closure is executed to configure the deployment descriptor. The [DeploymentDescriptor] is passed to the closure as its delegate.
     *
     * @param configureClosure The closure.
     * @return This.
     */
    fun deploymentDescriptor(@DelegatesTo(value = DeploymentDescriptor::class, strategy = Closure.DELEGATE_FIRST) configureClosure: Closure<*>?): Ear {
        ConfigureUtil.configure<DeploymentDescriptor?>(configureClosure, forceDeploymentDescriptor())
        return this
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     *
     * The given action is executed to configure the deployment descriptor.
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    fun deploymentDescriptor(configureAction: Action<in DeploymentDescriptor?>): Ear {
        configureAction.execute(forceDeploymentDescriptor())
        return this
    }

    private fun forceDeploymentDescriptor(): DeploymentDescriptor {
        if (deploymentDescriptor == null) {
            deploymentDescriptor = getObjectFactory().newInstance<DefaultDeploymentDescriptor?>(DefaultDeploymentDescriptor::class.java)
        }
        return deploymentDescriptor!!
    }

    /**
     * A location for dependency libraries to include in the 'lib' directory of the EAR archive.
     */
    @Internal
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    fun getLib(): CopySpec {
        return (lib as CopySpecInternal).addChild()
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
     *
     *
     * The given closure is executed to configure a `CopySpec`. The [CopySpec] is passed to the closure as its delegate.
     *
     * @param configureClosure The closure.
     * @return The created `CopySpec`
     */
    fun lib(@DelegatesTo(value = CopySpec::class, strategy = Closure.DELEGATE_FIRST) configureClosure: Closure<*>?): CopySpec? {
        return ConfigureUtil.configure<CopySpec?>(configureClosure, getLib())
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
     *
     *
     * The given action is executed to configure a `CopySpec`.
     *
     * @param configureAction The action.
     * @return The created `CopySpec`
     * @since 3.5
     */
    fun lib(configureAction: Action<in CopySpec?>): CopySpec {
        val copySpec = getLib()
        configureAction.execute(copySpec)
        return copySpec
    }

    @get:Input
    abstract val generateDeploymentDescriptor: Property<Boolean?>?

    @get:Internal
    abstract val appDirectory: DirectoryProperty?

    companion object {
        const val EAR_EXTENSION: String = "ear"
    }
}
