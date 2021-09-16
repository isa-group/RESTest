#! /bin/bash

# YOU NEED TO RUN THIS SCRIPT FROM THE PARENT DIRECTORY, I.E., THE ONE CONTAINING THE SRC/ FOLDER AND THE RESTEST.JAR EXECUTABLE

# You can include custom options for the execution of each RESTest instance launched by this script, e.g.:
# ./launch_benchmark.sh deletepreviousresults=false
# ./launch_benchmark.sh deletepreviousresults false logToFile=true

n_props=0
n_java=0
n_java_command="ps a | grep java | grep -v -w "$(basename "$0")" | grep -v -w grep | wc -l"

for propsFile in `find src/test/resources/taas_eval -name props.properties`
do
	java -jar restest.jar $propsFile $@ &>/dev/null &
	((n_props+=1))
	sleep 1m
  n_current_java="$(eval $n_java_command)"
  if [[ "$n_props" != "$n_current_java" ]]; then
    echo "ERROR: Instance failed: $propsFile"
  else
    echo "Instance correctly deployed: $propsFile"
  fi
done

n_java_final="$(eval $n_java_command)"

if [[ "$n_props" != "$n_java_final" ]]; then
  echo "WARNING: The number of properties files and Java processes don't match"
else
  echo "SUCCESS: All instances running"
fi