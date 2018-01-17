organization  := "org.toktok"
name          := "streambot"
version       := "0.1.0"
scalaVersion  := "2.11.12"

// Build dependencies.
libraryDependencies ++= Seq(
  "org.bytedeco" % "javacv-platform" % "1.4",
  "org.slf4j" % "slf4j-log4j12" % "1.7.22",

  // organization.value % "tox4j-c_darwin-x86_64" % "0.2.0",
  organization.value % "tox4j-c_linux-x86_64" % "0.2.0",
  organization.value %% "tox4j-c" % "0.1.4"
)
