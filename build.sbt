ThisBuild / organization := "com.guizmaii"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalafmtCheck := true
ThisBuild / scalafmtSbtCheck := true

lazy val projectName = "JRubyConcurrentConstantMemoryExcel"

lazy val testKitLibs = Seq(
  "org.scalacheck" %% "scalacheck" % "1.14.0",
  "org.scalactic"  %% "scalactic"  % "3.0.5",
  "org.scalatest"  %% "scalatest"  % "3.0.5",
).map(_ % Test)

lazy val poi =
  ((version: String) =>
    Seq(
      "org.apache.poi" % "poi"       % version,
      "org.apache.poi" % "poi-ooxml" % version
    ))("4.0.1")

lazy val root =
  Project(id = projectName, base = file("."))
    .settings(moduleName := "root")
    .settings(noPublishSettings: _*)
    .aggregate(core)
    .dependsOn(core)

lazy val core =
  project
    .settings(moduleName := projectName)
    .settings(
      libraryDependencies ++= Seq(
        "com.github.tototoshi" %% "scala-csv" % "1.3.5",
        "com.github.pathikrit" %% "better-files" % "3.7.0"
      ) ++ poi ++ testKitLibs)

/**
  * Copied from Cats
  */
lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

inThisBuild(
  List(
    credentials += Credentials(Path.userHome / ".bintray" / ".credentials"),
    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    homepage := Some(url("https://github.com/guizmaii/JRubyConcurrentConstantMemoryExcel")),
    bintrayOrganization := Some("guizmaii"),
    bintrayReleaseOnPublish := true,
    publishMavenStyle := true,
    pomExtra := (
      <scm>
        <url>git@github.com:guizmaii/JRubyConcurrentConstantMemoryExcel.git</url>
        <connection>scm:git:git@github.com:guizmaii/JRubyConcurrentConstantMemoryExcel.git</connection>
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
