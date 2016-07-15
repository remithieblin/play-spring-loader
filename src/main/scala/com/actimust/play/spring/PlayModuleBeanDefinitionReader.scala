package com.actimust.play.spring

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import play.api.inject.Binding

trait PlayModuleBeanDefinitionReader {

  def bind(beanFactory: DefaultListableBeanFactory, binding: Binding[_])

}
