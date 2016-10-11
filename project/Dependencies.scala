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
    val seleniumVersion = "2.53.0"
    val jWebDriverVersion = "0.16.4"
    val gson = "2.6.2"
    val commonsPool = "2.4.2"
    val scalaPool = "0.3.0"
  }

  lazy val server = Def.setting(Seq(
    "io.github.andrebeat" %% "scala-pool" % Versions.scalaPool,
    "org.apache.commons" % "commons-pool2" % Versions.commonsPool % Compile,
    "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % "2.52.0" % Compile,
    "com.google.code.gson" % "gson" % Versions.gson % Compile,
    "com.machinepublishers" % "jbrowserdriver" % Versions.jWebDriverVersion % Compile,
    "org.seleniumhq.selenium" % "selenium-server" % Versions.seleniumVersion % Compile,
    "org.seleniumhq.selenium" % "selenium-java" % Versions.seleniumVersion % Compile,
    "org.jcodec" % "jcodec-javase" % Versions.jcodec % Compile,
    "com.lihaoyi" %% "upickle" % Versions.upickleVersion % Compile,
    "com.typesafe.akka" %% "akka-actor" % Versions.akka % Compile,
    "com.typesafe.akka" %% "akka-slf4j" % Versions.akka % Compile,
    "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging % Compile,
    "com.typesafe.akka" %% "akka-http-experimental" % Versions.akkaHttp % Compile
  ))
}