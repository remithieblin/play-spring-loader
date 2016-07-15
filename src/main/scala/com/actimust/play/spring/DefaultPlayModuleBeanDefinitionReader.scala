package com.actimust.play.spring

import java.lang.annotation.Annotation

import org.springframework.beans.factory.config.{AutowireCapableBeanFactory, BeanDefinition, ConstructorArgumentValues}
import org.springframework.beans.factory.support.{AutowireCandidateQualifier, DefaultListableBeanFactory, GenericBeanDefinition}
import org.springframework.core.annotation.AnnotationUtils
import play.api.inject._

import scala.collection.JavaConverters._

class DefaultPlayModuleBeanDefinitionReader extends PlayModuleBeanDefinitionReader {

  def bind(beanFactory: DefaultListableBeanFactory, binding: Binding[_]): Unit = {

    // Firstly, if it's an unqualified key being bound to an unqualified alias, then there is no need to
    // register anything, Spring by type lookups match all types of the registered bean, there is no need
    // to register aliases for other types.
    val isSimpleTypeAlias = binding.key.qualifier.isEmpty &&
      binding.target.collect {
        case b @ BindingKeyTarget(key) if key.qualifier.isEmpty => b
      }.nonEmpty

    if (!isSimpleTypeAlias) {

      val beanDef = new GenericBeanDefinition()

      val beanName = binding.key.toString()

      // Add qualifier if it exists
      binding.key.qualifier match {
        case Some(QualifierClass(clazz)) =>
          beanDef.addQualifier(new AutowireCandidateQualifier(clazz))
        case Some(QualifierInstance(instance)) =>
          beanDef.addQualifier(qualifierFromInstance(instance))
        case None =>
          // No qualifier, so that means this is the primary binding for that type.
          // Setting primary means that if there are both qualified beans and this bean for the same type,
          // when an unqualified lookup is done, this one will be selected.
          beanDef.setPrimary(true)
      }

      // Start with a scope of prototype, if it's singleton, we'll explicitly set that later
      beanDef.setScope(BeanDefinition.SCOPE_PROTOTYPE)
      // Choose autowire constructor
      beanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)

      binding.target match {
        case None =>
          beanDef.setBeanClass(binding.key.clazz)
          SpringBuilder.maybeSetScope(beanDef, binding.key.clazz)

        case Some(ConstructionTarget(clazz)) =>
          // Bound to an implementation, set the impl class as the bean class.
          // In this case, the key class is ignored, since Spring does not key beans by type, but a bean is eligible
          // for autowiring for all supertypes/interafaces.

          if(clazz.isInterface) {
            beanDef.setBeanClass(classOf[BindingKeyFactoryBean[_]])
            val args = new ConstructorArgumentValues()
            args.addIndexedArgumentValue(0, clazz)
            args.addIndexedArgumentValue(1, binding.key.clazz)
            args.addIndexedArgumentValue(2, beanFactory)
            beanDef.setConstructorArgumentValues(args)
          } else {
            beanDef.setBeanClass(clazz.asInstanceOf[Class[_]])
            SpringBuilder.maybeSetScope(beanDef, clazz.asInstanceOf[Class[_]])
          }

          beanDef.setPrimary(false)

        case Some(ProviderConstructionTarget(providerClass)) =>

          val providerBeanName = providerClass.toString

          if (!beanFactory.containsBeanDefinition(providerBeanName)) {

            // The provider itself becomes a bean that gets autowired
            val providerBeanDef = new GenericBeanDefinition()
            providerBeanDef.setBeanClass(providerClass)
            providerClass.getAnnotations.filter(_.annotationType().getName == "javax.inject.Named").foreach {
              a: Annotation => beanDef.addQualifier(qualifierFromInstance(a))
            }
            providerBeanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
            providerBeanDef.setScope(BeanDefinition.SCOPE_SINGLETON)
            providerBeanDef.setAutowireCandidate(false)
            beanFactory.registerBeanDefinition(providerBeanName, providerBeanDef)
          }

          // And then the provider bean gets used as the factory bean, calling its get method, for the actual bean
          beanDef.setFactoryBeanName(providerBeanName)
          beanDef.setFactoryMethodName("get")
          beanDef.setPrimary(false)

        case Some(ProviderTarget(provider)) =>

          // We have an actual instance of that provider, we create a factory bean to wrap and invoke that provider instance
          beanDef.setBeanClass(classOf[ProviderFactoryBean[_]])
          val args = new ConstructorArgumentValues()
          args.addIndexedArgumentValue(0, provider)
          args.addIndexedArgumentValue(1, binding.key.clazz)
          args.addIndexedArgumentValue(2, beanFactory)
          beanDef.setConstructorArgumentValues(args)

        case Some(BindingKeyTarget(key)) =>

          // It's an alias, create a factory bean that will look up the alias
          beanDef.setBeanClass(classOf[BindingKeyFactoryBean[_]])
          val args = new ConstructorArgumentValues()
          args.addIndexedArgumentValue(0, key)
          args.addIndexedArgumentValue(1, binding.key.clazz)
          args.addIndexedArgumentValue(2, beanFactory)
          beanDef.setConstructorArgumentValues(args)
      }

      binding.scope match {
        case None =>
        // Do nothing, we've already defaulted or detected the scope
        case Some(scope) =>
          SpringBuilder.setScope(beanDef, scope)
      }

      beanFactory.registerBeanDefinition(beanName, beanDef)
    }
  }

  /**
   * Turns an instance of an annotation into a spring qualifier descriptor.
   */
  private def qualifierFromInstance(instance: Annotation) = {
    val annotationType = instance.annotationType()
    val qualifier = new AutowireCandidateQualifier(annotationType)
    AnnotationUtils.getAnnotationAttributes(instance).asScala.foreach {
      case (attribute, value) => qualifier.setAttribute(attribute, value)
    }

    qualifier
  }

}

object DefaultPlayModuleBeanDefinitionReader {

  def apply() = new DefaultPlayModuleBeanDefinitionReader()

}
