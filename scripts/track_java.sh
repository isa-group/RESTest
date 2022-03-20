#! /bin/bash

# WARNING: This script will not work in Windows and may not work in some Linux distributions
# Don't execute this script directly, but 'monitor.sh' instead

folder=monitor_data

rm -rf $folder
mkdir $folder

ps_options='-f'
if [[ "$OSTYPE" == "linux-gnu"* || "$OSTYPE" == "darwin"* || "$OSTYPE" == "freebsd"* ]]; then
  ps_options+='A'
else
  ps_options+='a'
fi

while [ true ]; do
  current_date_time=`date +%s`

  # Save memory and CPU info with command 'top'
  top -o -PID -c -b -n 1 -w 512 | grep -e 'top -' -e 'Tasks:' -e '%Cpu(s):' -e 'MiB Mem :' \
    -e 'MiB Swap:' -e '.*PID.*USER' -e 'java -jar restest.jar' | grep -v "top -o -PID -c -b -n 1" |
    grep -v -w "$(basename "$0")" | grep -v -w grep > $folder/mem_cpu_"$current_date_time".txt

  # Save disk info
  du -sh > $folder/disk_"$current_date_time".txt
  for propsFile in `ls target/allure-results`
  do
    du -sh `find -name $propsFile -type d` >> $folder/disk_"$current_date_time".txt
  done

  sleep 1m
done