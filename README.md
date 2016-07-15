# Play Spring Loader


This is a application loader for Play applications that runs with Spring as the DI.

The project targets Play 2.5.4

To use in your Play application project:

1. Checkout this repo locally
2. sbt publishLocal
3. add the dependency in your build.sbt file: "com.actimust"% "play-spring-loader" % "1.0-SNAPSHOT"
4. configure the loader in the conf file: play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"
