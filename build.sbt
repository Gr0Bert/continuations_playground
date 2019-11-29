ThisBuild / scalaVersion := "2.13.1"

lazy val continuationsPlayground = project
	.settings(
		name := "continuations-playground",
		version := "0.1",
		libraryDependencies ++= Seq(
			"org.typelevel" %% "cats-core" % "2.0.0",
			"org.typelevel" %% "cats-effect" % "2.0.0",
		)
	)
	.in(file("continuations-playground"))

lazy val docs = project
	.in(file("continuations-playground-docs"))
	.enablePlugins(MdocPlugin)
	.dependsOn(continuationsPlayground)
