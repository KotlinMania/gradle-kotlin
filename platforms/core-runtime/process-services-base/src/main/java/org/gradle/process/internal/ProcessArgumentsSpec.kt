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
package org.gradle.process.internal

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.GUtil
import java.util.Arrays

class ProcessArgumentsSpec(private val hasExecutable: HasExecutable) {
    internal interface HasExecutable {
        var executable: String?
    }

    private val arguments: MutableList<Any> = ArrayList<Any>()
    val argumentProviders: MutableList<CommandLineArgumentProvider> = ArrayList<CommandLineArgumentProvider>()

    val commandLine: MutableList<String?>
        get() {
            val commandLine: MutableList<String?> = ArrayList<String?>()
            commandLine.add(hasExecutable.executable)
            commandLine.addAll(this.allArguments)
            return commandLine
        }

    fun commandLine(vararg arguments: Any?): ProcessArgumentsSpec {
        commandLine(Arrays.asList<Any?>(*arguments))
        return this
    }

    fun commandLine(args: Iterable<*>): ProcessArgumentsSpec {
        val argsList: MutableList<Any?> = Lists.newArrayList<Any?>(args)
        hasExecutable.executable = argsList.get(0)
        setArgs(argsList.subList(1, argsList.size))
        return this
    }

    val allArguments: MutableList<String?>
        get() {
            val allArgs: MutableList<String?>
            val args = this.args
            if (args == null) {
                allArgs = ArrayList<String?>()
            } else {
                allArgs = ArrayList<String?>(args)
            }
            for (argumentProvider in argumentProviders) {
                Iterables.addAll<String?>(allArgs, CollectionUtils.toStringList(argumentProvider.asArguments()))
            }
            return allArgs
        }

    fun args(vararg args: Any?): ProcessArgumentsSpec {
        requireNotNull(args) { "args == null!" }
        args(Arrays.asList<Any?>(*args))
        return this
    }

    fun args(args: Iterable<*>): ProcessArgumentsSpec {
        GUtil.addToCollection(arguments, true, args)
        return this
    }

    fun setArgs(arguments: MutableList<String?>): ProcessArgumentsSpec {
        this.arguments.clear()
        args(arguments)
        return this
    }

    fun setArgs(arguments: Iterable<*>): ProcessArgumentsSpec {
        this.arguments.clear()
        args(arguments)
        return this
    }

    val args: MutableList<String?>
        get() {
            val args: MutableList<String?> = ArrayList<String?>()
            for (argument in arguments) {
                args.add(argument.toString())
            }
            return args
        }
}
