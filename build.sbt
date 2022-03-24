name := "ergo-names-backend"

scalaVersion := "2.12.15"

// Scala dependencies
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.2"
libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "4.0.7"
libraryDependencies += "io.github.dav009" %% "ergopuppet" % "0.0.0+28-8ee0ca24+20220219-2144"  % Test
libraryDependencies += "org.mockito" % "mockito-core" % "4.3.1" % Test
libraryDependencies += "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
libraryDependencies += "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
libraryDependencies += "com.amazonaws" % "aws-java-sdk-secretsmanager" % "1.12.181"

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", xs @ _*) => MergeStrategy.discard
 case x => MergeStrategy.first
}
