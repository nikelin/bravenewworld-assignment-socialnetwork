import sbt._

object Dependencies {

  object Versions {
    val slf4j = "1.7.12"
    val slf4jnop = "1.6.4"
    val scala = "2.11.8"
    val akka = "2.4.4"
    val akkaHttp = "1.0"
    val logback = "1.1.3"
    val scalaLogging = "3.1.0"
    val upickleVersion = "0.4.0"
    val jcodec = "0.1.9"
  }

  lazy val server = Def.setting(Seq(
    "org.jcodec" % "jcodec-javase" % Versions.jcodec % Compile,
    "com.lihaoyi" %% "upickle" % Versions.upickleVersion % Compile,
    "com.typesafe.akka" %% "akka-actor" % Versions.akka % Compile,
    "com.typesafe.akka" %% "akka-slf4j" % Versions.akka % Compile,
    "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging % Compile,
    "com.typesafe.akka" %% "akka-http-experimental" % Versions.akkaHttp % Compile
  ))
}