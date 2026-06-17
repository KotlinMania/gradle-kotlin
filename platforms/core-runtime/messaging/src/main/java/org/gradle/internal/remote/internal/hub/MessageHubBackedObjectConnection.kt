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
package org.gradle.internal.remote.internal.hub

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ThreadSafe
import org.gradle.internal.dispatch.BoundedDispatch
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.dispatch.ProxyDispatchAdapter
import org.gradle.internal.dispatch.ReflectionDispatch
import org.gradle.internal.dispatch.StreamCompletion
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.remote.ObjectConnection
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.internal.serialize.SerializerRegistry
import org.gradle.internal.serialize.StatefulSerializer
import org.gradle.internal.serialize.kryo.TypeSafeSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.Volatile

class MessageHubBackedObjectConnection(executorFactory: ExecutorFactory, completion: ConnectCompletion) : ObjectConnection {
    private val hub: MessageHub
    private val unrecoverableErrorHandlers: MutableList<Action<Throwable?>> = ArrayList<Action<Throwable?>>()
    private var completion: ConnectCompletion?
    private var connection: RemoteConnection<InterHubMessage?>? = null

    //    private ClassLoader methodParamClassLoader;
    private val paramSerializers: MutableList<SerializerRegistry?> = ArrayList<SerializerRegistry?>()
    private val methodParamClassLoaders: MutableSet<ClassLoader?> = HashSet<ClassLoader?>()

    @Volatile
    private var aborted = false

    init {
        val errorHandler: Action<Throwable?> = object : Action<Throwable?> {
            override fun execute(throwable: Throwable?) {
                var current = throwable
                for (handler in unrecoverableErrorHandlers) {
                    try {
                        handler.execute(current)
                    } catch (e: Throwable) {
                        current = DefaultMultiCauseException("Error in unrecoverable error handler: " + handler, e, throwable)
                    }
                }
            }
        }
        this.hub = MessageHub(completion.toString(), executorFactory, errorHandler)
        this.completion = completion
        this.addUnrecoverableErrorHandler(object : Action<Throwable?> {
            override fun execute(throwable: Throwable?) {
                if (!aborted && !Thread.currentThread().isInterrupted()) {
                    LOGGER.error("Unexpected exception thrown.", throwable)
                }
            }
        })
    }

    override fun useJavaSerializationForParameters(incomingMessageClassLoader: ClassLoader?) {
        methodParamClassLoaders.add(incomingMessageClassLoader)
    }

    override fun <T> addIncoming(type: Class<T?>, instance: T?) {
        if (connection != null) {
            throw GradleException("Cannot add incoming message handler after connection established.")
        }
        // we don't want to add core classloader explicitly here.
        if (type.getClassLoader() !== javaClass.getClassLoader()) {
            methodParamClassLoaders.add(type.getClassLoader())
        }
        val handler: Dispatch<MethodInvocation?> = DispatchWrapper<T?>(instance)
        hub.addHandler(type.getName(), handler)
    }

    override fun <T> addOutgoing(type: Class<T?>): T? {
        if (connection != null) {
            throw GradleException("Cannot add outgoing message transmitter after connection established.")
        }
        methodParamClassLoaders.add(type.getClassLoader())
        val adapter = ProxyDispatchAdapter<T?>(hub.getOutgoing<MethodInvocation?>(type.getName(), MethodInvocation::class.java), type, ThreadSafe::class.java)
        return adapter.source
    }

    override fun useParameterSerializers(serializer: SerializerRegistry?) {
        this.paramSerializers.add(serializer)
    }

    override fun connect() {
        val methodParamClassLoader: ClassLoader?
        if (methodParamClassLoaders.size == 0) {
            methodParamClassLoader = javaClass.getClassLoader()
        } else if (methodParamClassLoaders.size == 1) {
            methodParamClassLoader = methodParamClassLoaders.iterator().next()
        } else {
            methodParamClassLoader = CachingClassLoader(MultiParentClassLoader(methodParamClassLoaders))
        }
        val argsSerializer: MethodArgsSerializer = DefaultMethodArgsSerializer(paramSerializers, JavaSerializationBackedMethodArgsSerializer(methodParamClassLoader))

        val serializer: StatefulSerializer<InterHubMessage?> = InterHubMessageSerializer(
            TypeSafeSerializer<MethodInvocation?>(
                MethodInvocation::class.java,
                MethodInvocationSerializer(
                    methodParamClassLoader,
                    argsSerializer
                )
            )
        )

        connection = completion!!.create<InterHubMessage?>(serializer)
        hub.addConnection(connection)
        hub.noFurtherConnections()
        completion = null
    }

    override fun requestStop() {
        hub.requestStop()
    }

    override fun stop() {
        // TODO:ADAM - need to cleanup completion too, if not used
        CompositeStoppable.stoppable(hub, connection!!).stop()
    }

    override fun abort() {
        aborted = true
        stop()
    }

    override fun addUnrecoverableErrorHandler(handler: Action<Throwable?>?) {
        unrecoverableErrorHandlers.add(handler!!)
    }

    private class DispatchWrapper<T>(private val instance: T?) : BoundedDispatch<MethodInvocation?>, StreamFailureHandler {
        private val handler: Dispatch<MethodInvocation?>

        init {
            this.handler = ReflectionDispatch(instance)
        }

        override fun endStream() {
            if (instance is StreamCompletion) {
                (instance as StreamCompletion).endStream()
            }
        }

        override fun dispatch(message: MethodInvocation?) {
            handler.dispatch(message)
        }

        override fun handleStreamFailure(t: Throwable?) {
            if (instance is StreamFailureHandler) {
                (instance as StreamFailureHandler).handleStreamFailure(t)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(MessageHubBackedObjectConnection::class.java)
    }
}
