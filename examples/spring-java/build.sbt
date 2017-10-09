name := """spring-java"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.3"

val SpringVersion = "4.3.10.RELEASE"

libraryDependencies ++= Seq(
  "com.actimust" % "play-spring-loader" % "1.0.0-SNAPSHOT",
  "org.springframework" % "spring-core" % SpringVersion,
  "org.springframework" % "spring-expression" % SpringVersion,
  "org.springframework" % "spring-aop" % SpringVersion
)