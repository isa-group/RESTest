#! /bin/bash

# YOU NEED TO RUN THIS SCRIPT FROM THE PARENT DIRECTORY, I.E., THE ONE CONTAINING THE SRC/ FOLDER AND THE RESTEST.JAR EXECUTABLE

# You can include custom options for the execution of each RESTest instance launched by this script, e.g.:
# ./launch_benchmark.sh deletepreviousresults=false
# ./launch_benchmark.sh deletepreviousresults false logToFile=true

# Start monitoring RESTest instances
./monitor_java.sh &

ps_options='-f'
if [[ "$OSTYPE" == "linux-gnu"* || "$OSTYPE" == "darwin"* || "$OSTYPE" == "freebsd"* ]]; then
  ps_options+='A'
else
  ps_options+='a'
fi

propsFiles_command="find src/test/resources/taas_eval -name props.properties"
n_props=`$propsFiles_command | wc -l`
n_java_command="ps $ps_options | grep java | grep -v monitor_java | grep -v -w "$(basename "$0")" | grep -v -w grep"

for propsFile in `$propsFiles_command`
do
	java -jar restest.jar $propsFile $@ &>/dev/null &
	sleep 1m
	current_java="$(eval "$n_java_command" | grep $propsFile | wc -l)"
  if [[ "$current_java" == 0 ]]; then
    echo "ERROR: Instance failed: $propsFile"
  else
    echo "Instance correctly deployed: $propsFile"
  fi
done

n_java_final="$(eval $n_java_command | wc -l)"

if [[ "$n_props" != "$n_java_final" ]]; then
  echo "WARNING: The number of properties files and Java processes don't match"
else
  echo "SUCCESS: All instances running"
fi

# Save a file linking the PIDs to the RESTest instances
ps $ps_options | grep "java -jar restest.jar" | grep -v -w "$(basename "$0")" | grep -v -w grep > pids_restest.txt