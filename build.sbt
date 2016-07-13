
version := "1.0-SNAPSHOT"
organization := "com.rthieblin"
scalaVersion := "2.11.8"

name := "play-spring-loader"
crossPaths := false


lazy val root = project in file(".")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.5.4",
  "org.springframework" % "spring-context" % "4.2.1.RELEASE"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "mandubian maven bintray" at "https://dl.bintray.com/mandubian/maven"