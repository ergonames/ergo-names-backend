name := "ergo-names-backend"

scalaVersion := "2.12.15"

libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "4.0.7"
libraryDependencies += "io.github.dav009" %% "ergopuppet" % "0.0.0+28-8ee0ca24+20220219-2144"  % Test
libraryDependencies += ("org.scorexfoundation" %% "sigma-state" % "4.0.5" ).classifier("tests")
