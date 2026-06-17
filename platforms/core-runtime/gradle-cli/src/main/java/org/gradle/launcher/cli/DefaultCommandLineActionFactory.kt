/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.launcher.cli

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.cli.CommandLineArgumentException
import org.gradle.cli.CommandLineParser
import org.gradle.cli.OptionCategory
import org.gradle.cli.ParsedCommandLine
import org.gradle.configuration.DefaultBuildClientMetaData
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.BuildClientMetaData
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.Actions
import org.gradle.internal.buildevents.BuildExceptionReporter
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.BasicGlobalScopeServices
import org.gradle.internal.service.scopes.Scope
import org.gradle.launcher.bootstrap.CommandLineActionFactory
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.cli.converter.BuildLayoutConverter
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter
import org.gradle.launcher.cli.converter.InitialPropertiesConverter
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions
import org.gradle.launcher.cli.internal.HelpRenderer
import org.gradle.launcher.cli.internal.VersionInfoRenderer
import java.util.function.Consumer

/**
 *
 * Responsible for converting a set of command-line arguments into a [Runnable] action.
 */
open class DefaultCommandLineActionFactory : CommandLineActionFactory {
    /**
     *
     * Converts the given command-line arguments to an [Action] which performs the action requested by the
     * command-line args.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    override fun convert(args: MutableList<String>): CommandLineActionFactory.CommandLineExecution {
        val loggingServices = createLoggingServices()

        val loggingConfiguration: LoggingConfiguration = DefaultLoggingConfiguration()

        return WithLogging(
            loggingServices,
            args,
            loggingConfiguration,
            ParseAndBuildAction(loggingServices, args),
            BuildExceptionReporter(
                loggingServices.get<StyledTextOutputFactory?>(StyledTextOutputFactory::class.java)!!,
                loggingConfiguration,
                clientMetaData(),
                DefaultFailureFactory.withDefaultClassifier()
            )
        )
    }

    /**
     * This method is left visible so that tests can override it to inject [CommandLineActionCreator]s which
     * don't actually attempt to run the build per normally.
     *
     * @param loggingServices logging services to use when instantiating any [CommandLineActionCreator]s
     * @param basicServices basic services to use when instantiating any [CommandLineActionCreator]s
     * @param actionCreators collection of [CommandLineActionCreator]s to which to add a new [BuildActionsFactory]
     */
    @VisibleForTesting
    protected open fun createBuildActionFactoryActionCreator(loggingServices: ServiceRegistry?, basicServices: ServiceRegistry, actionCreators: MutableList<CommandLineActionCreator>) {
        actionCreators.add(BuildActionsFactory(loggingServices!!, basicServices, CurrentGradleInstallation.locate()))
    }

    /**
     * This method is left visible so that tests can override it to inject mocked [ServiceRegistry]s.
     *
     * @return the created [ServiceRegistry]
     */
    @VisibleForTesting
    protected open fun createLoggingServices(): ServiceRegistry {
        return LoggingServiceRegistry.newCommandLineProcessLogging()
    }

    private class BuiltInActionCreator : CommandLineActionCreator {
        override fun configureCommandLineParser(parser: CommandLineParser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.").hasCategory(OptionCategory.HELP)
            parser.option(VERSION, "version").hasDescription("Prints version information and exits.").hasCategory(OptionCategory.HELP)
            parser.option(VERSION_CONTINUE, "show-version").hasDescription("Prints version information and continues.").hasCategory(OptionCategory.HELP)
        }

        override fun createAction(parser: CommandLineParser, commandLine: ParsedCommandLine, parameters: Parameters): Action<in ExecutionListener?>? {
            if (commandLine.hasOption(HELP)) {
                return ShowUsageAction(parser, commandLine)
            }
            if (commandLine.hasOption(VERSION)) {
                return ShowVersionAction(parameters)
            }
            return null
        }
    }

    /**
     * This [CommandLineActionCreator] is responsible for handling any command line options that produce [ContinuingAction]s.
     */
    private class ContinuingActionCreator : NonParserConfiguringCommandLineActionCreator() {
        override fun createAction(parser: CommandLineParser?, commandLine: ParsedCommandLine, parameters: Parameters): ContinuingAction<in ExecutionListener?>? {
            if (commandLine.hasOption(VERSION_CONTINUE)) {
                return ContinuingAction { executionListener: ExecutionListener? -> ShowVersionAction(parameters).execute(executionListener) } as ContinuingAction<ExecutionListener?>
            }
            return null
        }
    }

    private class CommandLineParseFailureAction(private val parser: CommandLineParser, private val exception: Exception, private val args: MutableList<String>) : Action<ExecutionListener?> {
        override fun execute(executionListener: ExecutionListener) {
            System.err.println()
            System.err.println(exception.message)
            val output = HelpRenderer.render(parser, this.suggestedTaskSelector, false)
            System.err.print(output)
            executionListener.onFailure(exception)
        }

        val suggestedTaskSelector: String?
            get() {
                for (arg in args) {
                    if (!arg.startsWith("-")) {
                        return arg
                    }
                }
                return null
            }
    }

    private class ShowUsageAction(private val parser: CommandLineParser, private val commandLine: ParsedCommandLine) : Action<ExecutionListener?> {
        override fun execute(executionListener: ExecutionListener?) {
            val output = HelpRenderer.render(parser, this.suggestedTaskSelector, true)
            print(output)
        }

        val suggestedTaskSelector: String?
            get() {
                if (!commandLine.getExtraArguments().isEmpty()) {
                    return commandLine.getExtraArguments().get(0)
                } else {
                    return null
                }
            }
    }

    class ShowVersionAction(private val parameters: Parameters) : Action<ExecutionListener?> {
        override fun execute(executionListener: ExecutionListener?) {
            val versionInfo = VersionInfoRenderer.renderWithLauncherJvm(parameters.getDaemonParameters().getRequestedJvmCriteria().toString())
            print(versionInfo)
        }
    }

    /**
     * This [Action] will create new [Action]s that will be immediately executed.
     *
     * This class accomplishes this be maintaining a list of [CommandLineActionCreator]s which can each attempt to
     * create an [Action] from the given CLI args, and handles the logic for deciding whether or not to continue processing
     * based on whether the result is a [ContinuingAction] or not.  It allows for injecting alternate Creators which
     * won't actually attempt to run a build via the containing class' [.createBuildActionFactoryActionCreator]
     * method - this is why this class is not `static`.
     */
    private inner class ParseAndBuildAction(private val loggingServices: ServiceRegistry?, private val args: MutableList<String>) : Action<ExecutionListener?> {
        private val actionCreators: MutableList<CommandLineActionCreator>
        private val parser = CommandLineParser()

        init {
            actionCreators = ArrayList<CommandLineActionCreator>()
            actionCreators.add(BuiltInActionCreator())
            actionCreators.add(ContinuingActionCreator())
        }

        override fun execute(executionListener: ExecutionListener?) {
            val basicServices = createBasicGlobalServices(loggingServices)
            val buildEnvironmentConfigurationConverter = BuildEnvironmentConfigurationConverter(
                basicServices.get<BuildLayoutFactory?>(BuildLayoutFactory::class.java)!!,
                basicServices.get<FileCollectionFactory?>(FileCollectionFactory::class.java)!!
            )
            buildEnvironmentConfigurationConverter.configure(parser)

            // This must be added only during execute, because the actual constructor is called by various tests and this will not succeed if called then
            createBuildActionFactoryActionCreator(loggingServices, basicServices, actionCreators)
            configureCreators()

            var action: Action<in ExecutionListener?>?
            try {
                val commandLine = parser.parse(args)
                val parameters = buildEnvironmentConfigurationConverter.convertParameters(commandLine, null)
                action = createAction(parser, commandLine, parameters)
            } catch (e: CommandLineArgumentException) {
                action = CommandLineParseFailureAction(parser, e, args)
            }

            action.execute(executionListener)
        }

        fun configureCreators() {
            actionCreators.forEach(Consumer { creator: CommandLineActionCreator? -> creator!!.configureCommandLineParser(parser) })
        }

        fun createAction(parser: CommandLineParser?, commandLine: ParsedCommandLine?, parameters: Parameters?): Action<in ExecutionListener?> {
            val actions: MutableList<Action<in ExecutionListener?>?> = ArrayList<Action<in ExecutionListener?>?>(2)
            for (actionCreator in actionCreators) {
                val action: Action<in ExecutionListener?>? = actionCreator.createAction(parser!!, commandLine!!, parameters!!)
                if (action != null) {
                    actions.add(action)
                    if (action !is ContinuingAction<*>) {
                        break
                    }
                }
            }

            if (!actions.isEmpty()) {
                return Actions.composite<ExecutionListener?>(actions)
            }

            throw UnsupportedOperationException("No action factory for specified command-line arguments.")
        }
    }

    @VisibleForTesting
    open fun createBasicGlobalServices(loggingServices: ServiceRegistry?): ServiceRegistry {
        return ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.Global::class.java)
            .displayName("Basic global services")
            .parent(loggingServices!!)
            .parent(NativeServices.getInstance())
            .provider(BasicGlobalScopeServices())
            .build()
    }

    /**
     * Abstract type for any [CommandLineActionCreator] that does not make use of the [.configureCommandLineParser]
     * method.
     */
    private abstract class NonParserConfiguringCommandLineActionCreator : CommandLineActionCreator {
        override fun configureCommandLineParser(parser: CommandLineParser?) {
            // no-op
        }
    }

    private class WithLogging(
        private val loggingServices: ServiceRegistry,
        private val args: MutableList<String>,
        private val loggingConfiguration: LoggingConfiguration,
        private val action: Action<ExecutionListener?>,
        private val reporter: Action<Throwable?>?
    ) : CommandLineActionFactory.CommandLineExecution {
        override fun execute(executionListener: ExecutionListener) {
            val welcomeMessageConverter = BuildOptionBackedConverter<WelcomeMessageConfiguration?>(WelcomeMessageBuildOptions())
            val loggingBuildOptions = BuildOptionBackedConverter<LoggingConfiguration?>(LoggingConfigurationBuildOptions())
            val propertiesConverter = InitialPropertiesConverter()
            val buildLayoutConverter = BuildLayoutConverter()
            val layoutToPropertiesConverter = LayoutToPropertiesConverter(BuildLayoutFactory())
            val environmentVariables = System.getenv()

            var buildLayout = buildLayoutConverter.defaultValues()

            val parser = CommandLineParser()
            propertiesConverter.configure(parser)
            buildLayoutConverter.configure(parser)
            loggingBuildOptions.configure(parser)

            parser.allowUnknownOptions()
            parser.allowMixedSubcommandsAndOptions()

            val welcomeMessageConfiguration = WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE)

            try {
                val parsedCommandLine = parser.parse(args)
                val initialProperties = propertiesConverter.convert(parsedCommandLine)

                // Calculate build layout, for loading properties and other logging configuration
                buildLayout = buildLayoutConverter.convert(initialProperties, parsedCommandLine, null)

                // Read *.properties files
                val properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout)

                // Calculate the logging configuration
                loggingBuildOptions.convert(parsedCommandLine, properties.getProperties(), environmentVariables, loggingConfiguration)

                // Get configuration for showing the welcome message
                welcomeMessageConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, welcomeMessageConfiguration)
            } catch (e: CommandLineArgumentException) {
                // Ignore, deal with this problem later
            }

            val loggingManager = loggingServices.get<LoggingManagerFactory?>(LoggingManagerFactory::class.java)!!.createLoggingManager()
            loggingManager.setLevelInternal(loggingConfiguration.logLevel)
            loggingManager.start()
            try {
                val exceptionReportingAction: Action<ExecutionListener?> =
                    ExceptionReportingAction(
                        reporter, loggingManager,
                        NativeServicesInitializingAction(
                            buildLayout, loggingConfiguration, loggingManager,
                            WelcomeMessageAction(
                                buildLayout, welcomeMessageConfiguration,
                                DebugLoggerWarningAction(loggingConfiguration, action)
                            )
                        )
                    )
                exceptionReportingAction.execute(executionListener)
            } finally {
                loggingManager.stop()
            }
        }
    }

    companion object {
        private const val HELP = "h"
        private const val VERSION = "v"
        private const val VERSION_CONTINUE = "V"

        private fun clientMetaData(): BuildClientMetaData {
            return DefaultBuildClientMetaData(GradleLauncherMetaData())
        }
    }
}
