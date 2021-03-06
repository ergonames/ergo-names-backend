name := "ergo-names-backend"

scalaVersion := "2.12.15"

// Scala dependencies
libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "4.0.7"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2"
libraryDependencies += "io.github.dav009" %% "ergopuppet" % "0.0.0+28-8ee0ca24+20220219-2144"  % Test
libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests")  % Test

// Java dependencies
libraryDependencies += "org.mockito" % "mockito-core" % "4.3.1" % Test
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.11.46"
libraryDependencies += "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
libraryDependencies += "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
libraryDependencies += "com.amazonaws" % "aws-java-sdk-secretsmanager" % "1.12.181"

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", xs @ _*) => MergeStrategy.discard
 case x => MergeStrategy.first
}
