ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

lazy val root = (project in file("."))
  .settings(
    name := "doobie-shapeless",
      libraryDependencies ++= Seq(
        // Start with this one
        "org.tpolecat" %% "doobie-core"      % "1.0.0-RC4",

        // And add any of these as needed
        "org.tpolecat" %% "doobie-h2"        % "1.0.0-RC4",          // H2 driver 1.4.200 + type mappings.
        "org.tpolecat" %% "doobie-hikari"    % "1.0.0-RC4",          // HikariCP transactor.
        "org.tpolecat" %% "doobie-postgres"  % "1.0.0-RC4",          // Postgres driver 42.6.0 + type mappings.
        "org.tpolecat" %% "doobie-specs2"    % "1.0.0-RC4" % "test", // Specs2 support for typechecking statements.
        "org.tpolecat" %% "doobie-scalatest" % "1.0.0-RC4" % "test"  // ScalaTest support for typechecking statements.
      )

)
