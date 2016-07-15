package com.actimust.play.spring

import play.api.inject._
import play.api._
import play.core.{ DefaultWebCommands, WebCommands }

/**
 * A builder for creating Applications using Spring.
 */
class SpringApplicationBuilder (
                                 environment: Environment = Environment.simple(),
                                 configuration: Configuration = Configuration.empty,
                                 modules: Seq[Module] = Seq.empty,
                                 overrides: Seq[Module] = Seq.empty,
                                 disabled: Seq[Class[_]] = Seq.empty,
                                 eagerly: Boolean = false,
                                 loadConfiguration: Environment => Configuration = Configuration.load,
                                 global: Option[GlobalSettings] = None,
                                 loadModules: (Environment, Configuration) => Seq[Module] = SpringableModule.loadModules,
                                 beanReader: PlayModuleBeanDefinitionReader = DefaultPlayModuleBeanDefinitionReader()
                                 )  extends SpringBuilder[SpringApplicationBuilder](
  environment, configuration, modules, overrides, disabled, beanReader, eagerly
) {

  // extra constructor for creating from Java
  def this() = this(environment = Environment.simple())

  /**
   * Create a new Self for this immutable builder.
   * Provided by builder implementations.
   */
  override protected def newBuilder(environment: Environment,
                                    configuration: Configuration,
                                    modules: Seq[Module], overrides: Seq[Module],
                                    disabled: Seq[Class[_]],
                                    beanReader: PlayModuleBeanDefinitionReader,
                                    eagerly: Boolean): SpringApplicationBuilder = {
    copy(environment, configuration, modules, overrides, disabled, beanReader, eagerly)
  }


  override def prepareConfig(): SpringApplicationBuilder = {
    val initialConfiguration = loadConfiguration(environment)
    val appConfiguration = initialConfiguration ++ configuration
    val globalSettings = global.getOrElse(GlobalSettings(appConfiguration, environment))

    LoggerConfigurator(environment.classLoader).foreach {
      _.configure(environment)
    }

    if (shouldDisplayLoggerDeprecationMessage(appConfiguration)) {
      Logger.warn("Logger configuration in conf files is deprecated and has no effect. Use a logback configuration file instead.")
    }

    val loadedModules = loadModules(environment, appConfiguration)

    copy(configuration = appConfiguration)
      .bindings(loadedModules: Seq[Module])
      .bindings(
        Seq(new Module{
          def bindings(environment: Environment, configuration: Configuration) = Seq(
            bind[OptionalSourceMapper] to new OptionalSourceMapper(None),
            bind[WebCommands] to new DefaultWebCommands,
            bind[GlobalSettings] to globalSettings
          )
        })
      )
  }

  /**
   * Checks if the path contains the logger path
   * and whether or not one of the keys contains a deprecated value
   * TODO: extract to class to be reused across Guice and Spring
   *
   * @param appConfiguration The app configuration
   * @return Returns true if one of the keys contains a deprecated value, otherwise false
   */
  def shouldDisplayLoggerDeprecationMessage(appConfiguration: Configuration): Boolean = {
    import scala.collection.JavaConverters._
    import scala.collection.mutable

    val deprecatedValues = List("DEBUG", "WARN", "ERROR", "INFO", "TRACE", "OFF")

    // Recursively checks each key to see if it contains a deprecated value
    def hasDeprecatedValue(values: mutable.Map[String, AnyRef]): Boolean = {
      values.exists {
        case (_, value: String) if deprecatedValues.contains(value) =>
          true
        case (_, value: java.util.Map[String, AnyRef]) =>
          hasDeprecatedValue(value.asScala)
        case _ =>
          false
      }
    }

    if (appConfiguration.underlying.hasPath("logger")) {
      appConfiguration.underlying.getAnyRef("logger") match {
        case value: String =>
          hasDeprecatedValue(mutable.Map("logger" -> value))
        case value: java.util.Map[String, AnyRef] =>
          hasDeprecatedValue(value.asScala)
        case _ =>
          false
      }
    } else {
      false
    }
  }

  /**
   * Set the initial configuration loader.
   * Overrides the default or any previously configured values.
   */
  def loadConfig(loader: Environment => Configuration): SpringApplicationBuilder =
    copy(loadConfiguration = loader)

  /**
   * Set the initial configuration.
   * Overrides the default or any previously configured values.
   */
  def loadConfig(conf: Configuration): SpringApplicationBuilder =
    loadConfig(env => conf)

  /**
   * Set the module loader.
   * Overrides the default or any previously configured values.
   */
  def load(loader: (Environment, Configuration) => Seq[Module]): SpringApplicationBuilder =
    copy(loadModules = loader)

  /**
   * Create a new Play Application using this configured builder.
   */
  def build(): Application = {
    injector().instanceOf[Application]
  }

  override def injector(): Injector = {
    prepareConfig().bindings(createModule()).springInjector()
  }

  /**
   * Internal copy method with defaults.
   */
  private def copy(
                    environment: Environment = environment,
                    configuration: Configuration = configuration,
                    modules: Seq[Module] = modules,
                    overrides: Seq[Module] = overrides,
                    disabled: Seq[Class[_]] = disabled,
                    beanReader: PlayModuleBeanDefinitionReader = beanReader,
                    eagerly: Boolean = eagerly,
                    loadConfiguration: Environment => Configuration = loadConfiguration,
                    global: Option[GlobalSettings] = global,
                    loadModules: (Environment, Configuration) => Seq[Module] = loadModules
                    ): SpringApplicationBuilder =
    new SpringApplicationBuilder(environment, configuration, modules, overrides, disabled, eagerly, loadConfiguration, global, loadModules, beanReader)
}
