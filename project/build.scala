import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object IsuconBuild extends Build {
  val Organization = "com.github.m0t0k1ch1"
  val Name = "isucon"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.1"

  lazy val project = Project (
    "isucon",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),

        "com.typesafe.slick" %% "slick" % "1.0.1",
        "c3p0" % "c3p0" % "0.9.1.2",
        "mysql" % "mysql-connector-java" % "5.1.13",

        "org.scalatra" %% "scalatra-json" % "2.2.1",
        "org.json4s"   %% "json4s-jackson" % "3.2.4",

        "org.apache.directory.studio" % "org.apache.commons.io" % "2.4"
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) { base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding(
                "context",
                "_root_.org.scalatra.scalate.ScalatraRenderContext",
                importMembers = true,
                isImplicit = true
              )
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )
  )
}
