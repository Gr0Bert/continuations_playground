ThisBuild / scalaVersion := "2.13.1"

lazy val continuationsPlayground = project
	.settings(
		name := "continuations_playground",
		version := "0.1",
		libraryDependencies ++= Seq(
			"org.typelevel" %% "cats-core" % "2.0.0",
			"org.typelevel" %% "cats-effect" % "2.0.0",
		)
	)
	.in(file("continuations_playground"))

lazy val docs = project
	.in(file("continuations_playground_docs"))
	.enablePlugins(MdocPlugin)
	.dependsOn(continuationsPlayground)
  .settings(
		mdocOut := file(".")
	)
