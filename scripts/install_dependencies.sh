#! /bin/bash

mvn install:install-file -Dfile=lib/idl-choco-1.0.0.jar -DgroupId=es.us.isa -DartifactId=idl-choco -Dversion=1.0.0 -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file -Dfile=lib/idlreasoner-choco-1.0.0.jar
mvn install:install-file -Dfile=lib/MaleRegexTree.jar -DgroupId=es.us.isa -DartifactId=MaleRegexTree -Dversion=1.0.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib/RandomRegexTurtle.jar -DgroupId=es.us.isa -DartifactId=RandomRegexTurtle -Dversion=1.0.0 -Dpackaging=jar