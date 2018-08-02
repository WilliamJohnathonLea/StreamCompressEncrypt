name := "StreamCompressEncrypt"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.14",
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.20",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)
