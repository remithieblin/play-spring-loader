package com.actimust.play.spring

import java.io.File
import java.lang.annotation.Annotation
import javax.inject.Provider

import org.springframework.beans.TypeConverter
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver
import org.springframework.beans.factory.annotation.{AutowiredAnnotationBeanPostProcessor, QualifierAnnotationAutowireCandidateResolver}
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.{AutowireCapableBeanFactory, BeanDefinition, BeanDefinitionHolder}
import org.springframework.beans.factory.support._
import org.springframework.beans.factory.{FactoryBean, NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException}
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import play.api._
import play.api.inject._
import play.api._

import scala.collection.JavaConverters._
import scala.reflect.ClassTag


/**
 * A builder for creating Spring-backed Play Injectors.
 */
abstract class SpringBuilder[Self] protected (
    environment: Environment,
    configuration: Configuration,
    modules: Seq[Module],
    overrides: Seq[Module],
    disabled: Seq[Class[_]],
    beanReader: PlayModuleBeanDefinitionReader,
    eagerly: Boolean) {

  /**
   * Set the environment.
   */
  final def in(env: Environment): Self =
    copyBuilder(environment = env)

  /**
   * Set the environment path.
   */
  final def in(path: File): Self =
    copyBuilder(environment = environment.copy(rootPath = path))

  /**
   * Set the environment mode.
   */
  final def in(mode: Mode.Mode): Self =
    copyBuilder(environment = environment.copy(mode = mode))

  /**
   * Set the environment class loader.
   */
  final def in(classLoader: ClassLoader): Self =
    copyBuilder(environment = environment.copy(classLoader = classLoader))

  /**
   * Set the dependency initialization to eager.
   */
  final def eagerlyLoaded(): Self =
    copyBuilder(eagerly = true)

  /**
   * Add additional configuration.
   */
  final def configure(conf: Configuration): Self =
    copyBuilder(configuration = configuration ++ conf)

  /**
   * Add additional configuration.
   */
  final def configure(conf: Map[String, Any]): Self =
    configure(Configuration.from(conf))

  /**
   * Add additional configuration.
   */
  final def configure(conf: (String, Any)*): Self =
    configure(conf.toMap)

  /**
   * Add Guice modules, Play modules, or Play bindings.
   */
  final def bindings(bindModules: Seq[Module]): Self =
    copyBuilder(modules = modules ++ bindModules)

  /**
   * Disable modules by class.
   */
  final def disable(moduleClasses: Class[_]*): Self =
    copyBuilder(disabled = disabled ++ moduleClasses)

  /**
   * Override bindings using Spring modules, Play modules, or Play bindings.
   */
  final def overrides(overrideModules: Seq[Module]): Self =
    copyBuilder(overrides = overrides ++ overrideModules)

  /**
   * Override beanReader
   */
  final def withBeanReader(beanReader: PlayModuleBeanDefinitionReader): Self =
    copyBuilder(beanReader = beanReader)


  def applicationModule(): Seq[_] = createModule()

  /**
   *
   */
  def createModule(): Seq[Module] = {

    val injectorModule = new Module {
      def bindings(environment: Environment, configuration: Configuration) = Seq(
        bind[play.inject.Injector].to[play.inject.DelegateInjector]
    )}
    val enabledModules: Seq[Module] = filterOut(disabled, modules)
    val bindingModules: Seq[Module] = enabledModules :+ injectorModule
    val springableOverrides: Seq[Module] = overrides.map(SpringableModule.springable)
    bindingModules ++ springableOverrides
  }

  private def filterOut[A](classes: Seq[Class[_]], instances: Seq[A]): Seq[A] =
    instances.filterNot(o => classes.exists(_.isAssignableFrom(o.getClass)))


  /**
   * Disable module by class.
   */
  final def disable[T](implicit tag: ClassTag[T]): Self = disable(tag.runtimeClass)

  def prepareConfig(): Self

  def injector(): Injector = {
    springInjector()
  }

  def springInjector(): Injector = {
    val ctx = new AnnotationConfigApplicationContext()

    val beanFactory = ctx.getDefaultListableBeanFactory

    beanFactory.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver())

    val injector = new SpringInjector(beanFactory)
    // Register the Spring injector as a singleton first
    beanFactory.registerSingleton("play-injector", injector)

    //build modules
    val modulesToRegister = applicationModule()

    val disabledBindings = configuration.getStringSeq("play.bindings.disabled").getOrElse(Seq.empty)
    val disabledBindingClasses: Seq[Class[_]] = disabledBindings.map(className => loadClass(className))

    //register modules
    modulesToRegister.foreach {
      case playModule: Module =>
        playModule.bindings(environment, configuration)
          .filter(b => !disabledBindingClasses.contains(b.key.clazz))
          .foreach(b => beanReader.bind(beanFactory, b))
      case unknown => throw new PlayException(
        "Unknown module type",
        s"Module [$unknown] is not a Play module or a Guice module"
      )
    }

    val springConfig = configuration.getStringSeq("play.spring.configs").getOrElse(Seq.empty)
    val confClasses: Seq[Class[_]] = springConfig.map(className => loadClass(className))
    if(confClasses.nonEmpty) {
      ctx.register(confClasses:_*)
    }

    ctx.refresh()
    ctx.start()

    injector
  }

  def loadClass(className: String): Class[_] = {
    try {
      environment.classLoader.loadClass(className)
    } catch {
      case e: ClassNotFoundException => throw e
      case e: VirtualMachineError => throw e
      case e: ThreadDeath => throw e
      case e: Throwable =>
        throw new PlayException(s"Cannot load $className", s"[$className] was not loaded.", e)
    }
  }

  /**
   * Internal copy method with defaults.
   */
  private def copyBuilder(
    environment: Environment = environment,
    configuration: Configuration = configuration,
    modules: Seq[Module] = modules,
    overrides: Seq[Module] = overrides,
    disabled: Seq[Class[_]] = disabled,
    beanReader: PlayModuleBeanDefinitionReader = beanReader,
    eagerly: Boolean = eagerly): Self =
    newBuilder(environment, configuration, modules, overrides, disabled, beanReader, eagerly)

  /**
   * Create a new Self for this immutable builder.
   * Provided by builder implementations.
   */
  protected def newBuilder(
    environment: Environment,
    configuration: Configuration,
    modules: Seq[Module],
    overrides: Seq[Module],
    disabled: Seq[Class[_]],
    beanReader: PlayModuleBeanDefinitionReader,
    eagerly: Boolean): Self

}

private object SpringBuilder {
  /**
   * Set the scope on the given bean definition if a scope annotation is declared on the class.
   */
  def maybeSetScope(bd: GenericBeanDefinition, clazz: Class[_]) {
    clazz.getAnnotations.foreach { annotation =>
      if (annotation.annotationType().getAnnotations.exists(_.annotationType() == classOf[javax.inject.Scope])) {
        setScope(bd, annotation.annotationType())
      }
    }
  }

  /**
   * Set the given scope annotation scope on the given bean definition.
   */
  def setScope(bd: GenericBeanDefinition, clazz: Class[_ <: Annotation]) = {
    clazz match {
      case singleton if singleton == classOf[javax.inject.Singleton] =>
        bd.setScope(BeanDefinition.SCOPE_SINGLETON)
      case other =>
      // todo: use Jsr330ScopeMetaDataResolver to resolve and set scope
    }
  }
}

/**
 * A factory bean that wraps a binding key alias.
 */
class BindingKeyFactoryBean[T](key: BindingKey[T], objectType: Class[_], factory: DefaultListableBeanFactory) extends FactoryBean[T] {
  /**
   * The bean name, if it can be determined.
   *
   * Will either return a new bean name, or if the by type lookup should be done on request (in the case of an
   * unqualified lookup because it's cheaper to delegate that to Spring) then do it on request.  Will throw an
   * exception if a key for which no matching bean can be found is found.
   */
  lazy val beanName: Option[String] = {
    key.qualifier match {
      case None =>
        None
      case Some(QualifierClass(qualifier)) =>
        val candidates = factory.getBeanNamesForType(key.clazz)
        val matches = candidates.toList
          .map(name => new BeanDefinitionHolder(factory.getBeanDefinition(name), name))
          .filter { bdh =>
          bdh.getBeanDefinition match {
            case abd: AbstractBeanDefinition =>
              abd.hasQualifier(qualifier.getName)
            case _ => false
          }
        }.map(_.getBeanName)
        getNameFromMatches(matches)
      case Some(QualifierInstance(qualifier)) =>
        val candidates = factory.getBeanNamesForType(key.clazz)
        val matches = candidates.toList
          .map(name => new BeanDefinitionHolder(factory.getBeanDefinition(name), name))
          .filter( bdh => QualifierChecker.checkQualifier(bdh, qualifier, factory.getTypeConverter))
          .map(_.getBeanName)
        getNameFromMatches(matches)
    }
  }

  private def getNameFromMatches(candidates: Seq[String]): Option[String] = {
    candidates match {
      case Nil => throw new NoSuchBeanDefinitionException(key.clazz, "Binding alias for type " + objectType + " to " + key,
        "No bean found for binding alias")
      case single :: Nil => Some(single)
      case multiple => throw new NoUniqueBeanDefinitionException(key.clazz, multiple.asJava)
    }

  }

  def getObject = {
    beanName.fold(factory.getBean(key.clazz))(name => factory.getBean(name).asInstanceOf[T])
  }

  def getObjectType = objectType

  def isSingleton = false
}

/**
 * A factory bean that wraps a provider.
 */
class ProviderFactoryBean[T](provider: Provider[T], objectType: Class[_], factory: AutowireCapableBeanFactory)
  extends FactoryBean[T] {

  lazy val injectedProvider = {
    // Autowire the providers properties - Play needs this in a few places.
    val bpp = new AutowiredAnnotationBeanPostProcessor()
    bpp.setBeanFactory(factory)
    bpp.processInjection(provider)
    provider
  }

  def getObject = injectedProvider.get()

  def getObjectType = objectType

  def isSingleton = false
}

/**
 * Hack to expose the checkQualifier method as public.
 */
object QualifierChecker extends QualifierAnnotationAutowireCandidateResolver {

  /**
   * Override to expose as public
   */
  override def checkQualifier(bdHolder: BeanDefinitionHolder, annotation: Annotation, typeConverter: TypeConverter) = {
    bdHolder.getBeanDefinition match {
      case root: RootBeanDefinition => super.checkQualifier(bdHolder, annotation, typeConverter)
      case nonRoot =>
        val bdh = new BeanDefinitionHolder(RootBeanDefinitionCreator.create(nonRoot), bdHolder.getBeanName)
        super.checkQualifier(bdh, annotation, typeConverter)
    }
  }
}