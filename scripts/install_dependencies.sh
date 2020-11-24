mvn install:install-file -Dfile=lib/JSONmutator.jar -DgroupId=es.us.isa -DartifactId=json-mutator -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=lib/IDL2MiniZincMapper.jar -DgroupId=es.us.isa -DartifactId=idl-2-minizinc-mapper -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file -Dfile=lib/IDLreasoner.jar