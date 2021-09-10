#! /bin/bash

# YOU NEED TO RUN THIS SCRIPT FROM THE PARENT DIRECTORY, I.E., THE ONE CONTAINING THE SRC/ FOLDER AND THE RESTEST.JAR EXECUTABLE

# You can include custom options for the execution of each RESTest instance launched by this script, e.g.:
# ./launch_benchmark.sh deletepreviousresults=false
# ./launch_benchmark.sh deletepreviousresults false logToFile=true

for propsFile in `find src/test/resources/taas_eval -name props.properties`
do
	java -jar restest.jar $propsFile $@ &>/dev/null &
	sleep 1m
done