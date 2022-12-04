name := "ergo-names-backend"

scalaVersion := "2.12.15"

// Scala dependencies
libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "4.0.10"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2"
libraryDependencies += "io.github.dav009" %% "ergopuppet" % "0.0.0+28-8ee0ca24+20220219-2144"  % Test
libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests")  % Test
libraryDependencies += "com.lihaoyi" %% "requests" % "0.1.8"

// Dependencies for JSON logging. Do not change versions!
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "ch.qos.logback.contrib" % "logback-jackson" % "0.1.5"
libraryDependencies += "ch.qos.logback.contrib" % "logback-json-classic" % "0.1.5"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.5"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
libraryDependencies += "com.github.dwickern" %% "scala-nameof" % "4.0.0" % "provided"

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
