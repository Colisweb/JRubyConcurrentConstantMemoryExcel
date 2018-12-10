ThisBuild / organization := "com.guizmaii"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalafmtCheck := true
ThisBuild / scalafmtSbtCheck := true

lazy val testKitLibs = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.0",
  "org.scalactic"  %% "scalactic"  % "3.0.5",
  "org.scalatest"  %% "scalatest"  % "3.0.5",
).map(_ % Test)

lazy val root =
  Project(id = "easy_excel_jruby", base = file("."))
    .settings(moduleName := "root")
    .settings(noPublishSettings: _*)
    .aggregate(core, constantSpace)
    .dependsOn(core, constantSpace)

lazy val core =
  project
    .settings(moduleName := "easy_excel_jruby")
    .settings(
      libraryDependencies ++= Seq(
        "com.norbitltd" %% "spoiwo" % "1.4.1"
      ) ++ testKitLibs
    )

lazy val constantSpace =
  project
    .settings(moduleName := "easy_excel_jruby_constant_space")
    .settings(
      libraryDependencies ++= Seq(
        "com.norbitltd" %% "spoiwo" % "1.4.1",
        "com.nrinaudo" %% "kantan.csv" % "0.5.0",
        "com.github.pathikrit" %% "better-files" % "3.7.0"
      ) ++ testKitLibs
    )

/**
  * Copied from Cats
  */
def noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

inThisBuild(
  List(
    credentials += Credentials(Path.userHome / ".bintray" / ".credentials"),
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/guizmaii/easy_excel_jruby")),
    bintrayOrganization := Some("guizmaii"),
    bintrayReleaseOnPublish := true,
    publishMavenStyle := true,
    pomExtra := (
      <scm>
        <url>git@github.com:guizmaii/easy_excel_jruby.git</url>
        <connection>scm:git:git@github.com:guizmaii/easy_excel_jruby.git</connection>
      </scm>
        <developers>
          <developer>
            <id>guizmaii</id>
            <name>Jules Ivanic</name>
          </developer>
        </developers>
    )
  )
)

//// Aliases

/**
  * Copied from kantan.csv
  */
addCommandAlias("runBenchs", "benchmarks/jmh:run -i 10 -wi 10 -f 2 -t 1")

/**
  * Example of JMH tasks that generate flamegraphs.
  *
  * http://malaw.ski/2017/12/10/automatic-flamegraph-generation-from-jmh-benchmarks-using-sbt-jmh-extras-plain-java-too/
  */
addCommandAlias(
  "flame93",
  "benchmarks/jmh:run -f1 -wi 10 -i 20 PackerBenchmarkWithRealData -prof jmh.extras.Async:flameGraphOpts=--minwidth,2;verbose=true")
