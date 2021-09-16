#! /bin/bash

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
    pid_list="$(ps a | grep "$program" | grep -v -w "$script" |
        grep -v -w grep | awk '{print $1}')"
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

killprocs java
# killprocs "$1" # Uncomment this if you want to kill other processes than Java