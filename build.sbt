organization := "edu.mit.lib"
name := "montagu"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.12.2"

val Http4sVersion = "0.15.14"
val Rdf4JVersion = "2.2.2"

libraryDependencies ++= Seq(
 "org.http4s"         %% "http4s-blaze-server"   % Http4sVersion,
 "org.http4s"         %% "http4s-circe"          % Http4sVersion,
 "org.http4s"         %% "http4s-dsl"            % Http4sVersion,
 "org.eclipse.rdf4j"  %  "rdf4j-model"           % Rdf4JVersion,
 "org.eclipse.rdf4j"  %  "rdf4j-rio-jsonld"      % Rdf4JVersion,
 "org.eclipse.rdf4j"  %  "rdf4j-rio-turtle"      % Rdf4JVersion,
 "org.eclipse.rdf4j"  %  "rdf4j-repository-sail" % Rdf4JVersion,
 "org.eclipse.rdf4j"  %  "rdf4j-sail-memory"     % Rdf4JVersion,
 "com.amazonaws"      %  "aws-java-sdk-s3"       % "1.11.160",
 "ch.qos.logback"     %  "logback-classic"       % "1.2.1",
 "org.scalatest"      %% "scalatest"             % "3.0.1" % "test"
)
