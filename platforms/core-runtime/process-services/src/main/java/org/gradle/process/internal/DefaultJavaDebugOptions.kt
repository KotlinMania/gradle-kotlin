/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.process.internal

import com.google.common.base.Preconditions
import org.gradle.api.Action
import org.gradle.api.internal.ExternalProcessStartedListener
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.internal.PatternSets
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.services.FileSystems.default
import org.gradle.internal.reflect.Instantiator
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.util.concurrent.Executor

class DefaultExecActionFactory private constructor(
    protected val fileResolver: FileResolver,
    protected val fileCollectionFactory: FileCollectionFactory,
    instantiator: Instantiator,
    executor: Executor,
    protected val temporaryFileProvider: TemporaryFileProvider,
    buildCancellationToken: BuildCancellationToken,
    protected val objectFactory: ObjectFactory,
    protected val javaModuleDetector: JavaModuleDetector?,
    externalProcessStartedListener: ExternalProcessStartedListener?
) : ExecFactory {
    protected val executor: Executor
    protected val buildCancellationToken: BuildCancellationToken?
    protected val execHandleFactory: ClientExecHandleBuilderFactory
    protected val instantiator: Instantiator
    protected val externalProcessStartedListener: ExternalProcessStartedListener?

    init {
        this.buildCancellationToken = buildCancellationToken
        this.executor = executor
        this.instantiator = instantiator
        this.externalProcessStartedListener = externalProcessStartedListener
        // Create execHandleFactory bound to this instance that uses fileResolver, executor and buildCancellationToken passed to this factory,
        // this is important so that process directories are resolved correctly and process is stopped in the same scope
        this.execHandleFactory = DefaultClientExecHandleBuilderFactory.of(fileResolver, executor, buildCancellationToken)
    }

    fun newDecoratedExecAction(): ExecAction {
        val execAction = instantiator.newInstance<DefaultExecAction>(DefaultExecAction::class.java, execHandleFactory.newExecHandleBuilder())
        val listener = this.execHandleListener
        if (listener != null) {
            execAction.listener(listener)
        }
        return execAction
    }

    override fun newExecAction(): ExecAction {
        return DefaultExecAction(execHandleFactory.newExecHandleBuilder()!!)
    }

    override fun newDecoratedJavaForkOptions(): JavaForkOptionsInternal {
        val forkOptions = instantiator.newInstance<DefaultJavaForkOptions>(DefaultJavaForkOptions::class.java, objectFactory, fileResolver, fileCollectionFactory)
        forkOptions.setExecutable(Jvm.current().getJavaExecutable())
        return forkOptions
    }

    override fun newJavaForkOptions(): JavaForkOptionsInternal {
        val forkOptions = objectFactory.newInstance<DefaultJavaForkOptions>(DefaultJavaForkOptions::class.java, objectFactory, fileResolver, fileCollectionFactory)
        if (forkOptions.getExecutable() == null) {
            forkOptions.setExecutable(Jvm.current().getJavaExecutable())
        }
        return forkOptions
    }

    override fun toEffectiveJavaForkOptions(options: JavaForkOptionsInternal): EffectiveJavaForkOptions? {
        @Suppress("deprecation") val nonCachingPatternSetFactory = PatternSets.getNonCachingPatternSetFactory()
        // NOTE: We do not want/need a decorated version of JvmOptions or JavaDebugOptions because
        // these immutable instances are held across builds and will retain classloaders/services in the decorated object
        val fileCollectionFactory = DefaultFileCollectionFactory(
            fileResolver,
            DefaultTaskDependencyFactory.withNoAssociatedProject(),
            DefaultDirectoryFileTreeFactory(),
            nonCachingPatternSetFactory,
            PropertyHost.NO_OP,
            default
        )
        return options.toEffectiveJavaForkOptions(fileCollectionFactory)
    }

    fun newDecoratedJavaExecAction(): JavaExecAction {
        val forkOptions = newDecoratedJavaForkOptions()
        forkOptions.setExecutable(Jvm.current().getJavaExecutable())
        val javaExecAction = instantiator.newInstance<DefaultJavaExecAction>(DefaultJavaExecAction::class.java, newJavaExec())
        val listener = this.execHandleListener
        if (listener != null) {
            javaExecAction.listener(listener)
        }
        return javaExecAction
    }

    private val execHandleListener: ExecHandleListener?
        get() {
            if (externalProcessStartedListener == null) {
                return null
            }

            return object : ExecHandleListener {
                override fun beforeExecutionStarted(execHandle: ExecHandle?) {
                    val command = StringBuilder(execHandle!!.getCommand())
                    for (argument in execHandle.getArguments()) {
                        command.append(' ').append(argument)
                    }
                    externalProcessStartedListener.onExternalProcessStarted(command.toString(),  /* consumer */null)
                }

                override fun executionStarted(execHandle: ExecHandle?) {
                }

                override fun executionFinished(execHandle: ExecHandle?, execResult: ExecResult?) {
                }
            }
        }

    override fun newJavaExecAction(): JavaExecAction {
        return DefaultJavaExecAction(newJavaExec())
    }

    @Suppress("deprecation")
    override fun newExec(): ExecHandleBuilder {
        return DefaultExecHandleBuilder(execHandleFactory.newExecHandleBuilder()!!)
    }

    override fun newJavaExec(): JavaExecHandleBuilder {
        return JavaExecHandleBuilder(
            fileCollectionFactory,
            objectFactory,
            temporaryFileProvider,
            javaModuleDetector,
            newJavaForkOptions(),
            execHandleFactory.newExecHandleBuilder()!!
        )
    }

    override fun javaexec(action: Action<in JavaExecSpec>): ExecResult? {
        val execAction = newDecoratedJavaExecAction()
        action.execute(execAction)
        return execAction.execute()
    }

    override fun exec(action: Action<in ExecSpec>): ExecResult? {
        val execAction = newDecoratedExecAction()
        action.execute(execAction)
        return execAction.execute()
    }

    override fun forContext(): ExecFactory.Builder {
        return BuilderImpl(executor, temporaryFileProvider)
            .withInstantiator(instantiator)
            .withExternalProcessStartedListener(externalProcessStartedListener)
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withBuildCancellationToken(buildCancellationToken)
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector)
    }

    private class BuilderImpl(// The executor is always inherited from the parent
        private val executor: Executor,
        // The temporaryFileProvider is always inherited from the parent
        private val temporaryFileProvider: TemporaryFileProvider
    ) : ExecFactory.Builder {
        private var fileResolver: FileResolver? = null
        private var fileCollectionFactory: FileCollectionFactory? = null
        private var instantiator: Instantiator? = null
        private var buildCancellationToken: BuildCancellationToken? = null
        private var objectFactory: ObjectFactory? = null
        private var javaModuleDetector: JavaModuleDetector? = null

        private var externalProcessStartedListener: ExternalProcessStartedListener? = null

        override fun withFileResolver(fileResolver: FileResolver): ExecFactory.Builder {
            this.fileResolver = fileResolver
            return this
        }

        override fun withFileCollectionFactory(fileCollectionFactory: FileCollectionFactory): ExecFactory.Builder {
            this.fileCollectionFactory = fileCollectionFactory
            return this
        }

        override fun withInstantiator(instantiator: Instantiator): ExecFactory.Builder {
            this.instantiator = instantiator
            return this
        }

        override fun withObjectFactory(objectFactory: ObjectFactory): ExecFactory.Builder {
            this.objectFactory = objectFactory
            return this
        }

        override fun withJavaModuleDetector(javaModuleDetector: JavaModuleDetector?): ExecFactory.Builder {
            this.javaModuleDetector = javaModuleDetector
            return this
        }

        override fun withBuildCancellationToken(buildCancellationToken: BuildCancellationToken?): ExecFactory.Builder {
            this.buildCancellationToken = buildCancellationToken
            return this
        }

        override fun withExternalProcessStartedListener(externalProcessStartedListener: ExternalProcessStartedListener?): ExecFactory.Builder {
            this.externalProcessStartedListener = externalProcessStartedListener
            return this
        }

        override fun withoutExternalProcessStartedListener(): ExecFactory.Builder {
            this.externalProcessStartedListener = null
            return this
        }

        override fun build(): ExecFactory {
            Preconditions.checkState(fileResolver != null, "fileResolver is not set")
            Preconditions.checkState(fileCollectionFactory != null, "fileCollectionFactory is not set")
            Preconditions.checkState(instantiator != null, "instantiator is not set")
            Preconditions.checkState(buildCancellationToken != null, "buildCancellationToken is not set")
            Preconditions.checkState(objectFactory != null, "objectFactory is not set")
            return DefaultExecActionFactory(
                fileResolver!!,
                fileCollectionFactory!!,
                instantiator!!,
                executor,
                temporaryFileProvider,
                buildCancellationToken!!,
                objectFactory!!,
                javaModuleDetector,
                externalProcessStartedListener
            )
        }
    }

    companion object {
        @JvmStatic
        fun of(
            fileResolver: FileResolver,
            fileCollectionFactory: FileCollectionFactory,
            instantiator: Instantiator,
            executorFactory: ExecutorFactory,
            temporaryFileProvider: TemporaryFileProvider,
            buildCancellationToken: BuildCancellationToken,
            objectFactory: ObjectFactory
        ): DefaultExecActionFactory {
            return DefaultExecActionFactory(
                fileResolver,
                fileCollectionFactory,
                instantiator,
                executorFactory.create("Exec process"),
                temporaryFileProvider,
                buildCancellationToken,
                objectFactory,
                null,
                null
            )
        }
    }
}
