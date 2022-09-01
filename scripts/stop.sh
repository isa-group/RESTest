#! /bin/bash

# CAREFUL! This script will likely kill any process running Java
processContains='java'
processNotContains='allure'
# We have a script called 'monitor_java.sh', whose process will also be killed by this script

ps_options='a'
if [[ "$OSTYPE" == "linux-gnu"* || "$OSTYPE" == "darwin"* || "$OSTYPE" == "freebsd"* ]]; then
  ps_options='-A'
fi

# Modified version of pkill: kills all processes CONTAINING a string
pkill_modif()
{
    local pid_list signal program script
    script="$(basename "$0")"
    signal="$1"
    program="$2"
    # shellcheck disable=2086
    # shellcheck disable=2009
    # Get the list of PIDs. Be careful to exclude grep and the script itself
    pid_list="$(ps $ps_options | grep "$program" | grep -v "$processNotContains" |
        grep -v -w "$script" | grep -v -w grep | awk '{print $1}')"
    if [ -z "$pid_list" ]
    then
        # No process found
        return 1
    else
        # shellcheck disable=2086
        kill $signal $pid_list
        return 0
    fi
}

killprocs()
{
    count=0
    if pkill_modif -TERM "$1"
    then
        # Wait for up to 10 seconds for the process(es) to exit.
        while ((count<10))
        do
            pkill_modif -0 "$1" || return
            sleep 1
            ((count+=1))
        done
        pkill_modif -KILL "$1"
    fi
}

killprocs $processContains
# killprocs "$1" # Uncomment this if you want to kill other processes than Java