package com.actimust.play.spring

import controllers.Assets
import play.api.ApplicationLoader.Context
import play.api._
import play.api.inject._
import play.core.WebCommands

/**
 * based on the awesome work of jroper:
 * https://github.com/jroper/play-spring
 */
class SpringApplicationLoader(protected val initialBuilder: SpringApplicationBuilder) extends ApplicationLoader {

  // empty constructor needed for instantiating via reflection
  def this() = this(new SpringApplicationBuilder)

  def load(context: Context) = {

    builder(context).build()
  }

  /**
   * Construct a builder to use for loading the given context.
   */
  protected def builder(context: ApplicationLoader.Context): SpringApplicationBuilder = {
    initialBuilder
      .in(context.environment)
      .loadConfig(context.initialConfiguration)
      .overrides(overrides(context): Seq[Module])
  }

  /**
   * Override some bindings using information from the context. The default
   * implementation of this method provides bindings that most applications
   * should include.
   */
  protected def overrides(context: ApplicationLoader.Context): Seq[Module] = {
    SpringApplicationLoader.defaultOverrides(context)
  }
}

private object SpringApplicationLoader {

  /**
   * The default overrides provided by the Scala and Java SpringApplicationLoaders.
   */
  def defaultOverrides(context: ApplicationLoader.Context) = {
    Seq(
      new Module {
        def bindings(environment: Environment, configuration: Configuration) = Seq(
          bind[OptionalSourceMapper] to new OptionalSourceMapper(context.sourceMapper),
          bind[WebCommands] to context.webCommands,
          bind[Assets].to[Assets],
          bind[play.Configuration].to[play.Configuration]
        )
      }
    )
  }

}