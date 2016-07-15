package com.actimust.play.spring

import org.springframework.beans.BeanInstantiationException
import org.springframework.beans.factory.config.{AutowireCapableBeanFactory, BeanDefinition}
import org.springframework.beans.factory.{BeanCreationException, NoSuchBeanDefinitionException}
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.support.{GenericBeanDefinition, DefaultListableBeanFactory}
import play.api.inject.{BindingKey, Injector, Modules, Module}
import play.api.{PlayException, Configuration, Environment}

import scala.reflect.ClassTag

class SpringInjector(factory: DefaultListableBeanFactory) extends Injector {

  private val bpp = new AutowiredAnnotationBeanPostProcessor()
  bpp.setBeanFactory(factory)

  def instanceOf[T](implicit ct: ClassTag[T]) = instanceOf(ct.runtimeClass).asInstanceOf[T]

  def instanceOf[T](clazz: Class[T]) = {
    getBean(clazz)
  }

  def getBean[T](clazz: Class[T]): T = {
    try {
      factory.getBean(clazz)
    } catch {

      case e: NoSuchBeanDefinitionException =>
        // if the class is a concrete type, attempt to create a just in time binding
        if (!clazz.isInterface /* todo check if abstract, how? */) {
          tryCreate(clazz)
        } else {
          throw e
        }

      case e: BeanInstantiationException =>
        throw e

      case e: BeanCreationException =>
        throw e

      case e: Exception => throw e
    }
  }

  override def instanceOf[T](key: BindingKey[T]): T = {
    getBean(key.clazz)
  }

  def tryCreate[T](clazz: Class[T]) = {
    val beanDef = new GenericBeanDefinition()
    beanDef.setScope(BeanDefinition.SCOPE_PROTOTYPE)
    SpringBuilder.maybeSetScope(beanDef, clazz)
    beanDef.setBeanClass(clazz)

    /**
     * Set primary to make sure this class is selected over the "parent" one declared in the bindings which is likely
     * to be an interface or provider.
     * See  interface play.api.routing.Router and actual class router.Routes
     */
    beanDef.setPrimary(true)

    beanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT)
    factory.registerBeanDefinition(clazz.toString, beanDef)
    factory.clearMetadataCache()

    val bean = instanceOf(clazz)

    // todo - this ensures fields get injected, see if there's a way that this can be done automatically
    bpp.processInjection(bean)
    bean
  }
}

object SpringableModule {

  def loadModules(environment: Environment, configuration: Configuration): Seq[Module] = {
    Modules.locate(environment, configuration) map springable
  }

  /**
   * Attempt to convert a module of unknown type to a GuiceableModule.
   */
  def springable(module: Any): Module = module match {
    case playModule: Module => playModule
//    case bin
    case unknown => throw new PlayException(
      "Unknown module type",
      s"Module [$unknown] is not a Play module"
    )
  }
}
