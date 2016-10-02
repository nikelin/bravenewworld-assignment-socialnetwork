name := "bnw-backend"
version := "1.0"

scalaVersion := "2.11.8"

lazy val root = (project in file("."))
  .enablePlugins(PlayJava)
  .settings(
    libraryDependencies ++= Dependencies.server.value,
    libraryDependencies += ws,
    routesGenerator := InjectedRoutesGenerator
  )

