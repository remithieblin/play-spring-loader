# Play Spring Loader

This is an application loader for Play applications that runs with Spring as the DI.

The project targets Play 2.6.x

To use in your Play application project:

1. add the dependency in your build.sbt file: "com.actimust"% "play-spring-loader" % "1.0.0-SNAPSHOT"
2. configure the loader in the conf file: play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"

The library is hosted on Sonatype. 

Example config for `scala` based app:

```sh
play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"

play.modules.enabled += "com.demo.spring.MyModule"

play.spring.configs += "config.AppConfig"
```

with:

```scala
package config

import org.springframework.context.annotation.{ComponentScan, Configuration}

@Configuration
@ComponentScan(Array("com.demo.spring", "controllers"))
class AppConfig  {

}
```

Example config for Java based app:

```sh
play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"

play.modules.enabled += "com.demo.spring.MyModule"

play.spring.configs = ["com.example.PlaySpringDIConfiguration"]
```

with:

```java
package com.example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class PlaySpringDIConfiguration {

}
```
