#! /bin/bash

# Careful with ps options. POSIX standard makes 'ps a' different from 'ps -a'
ps_options='a'
my_id="$(id -un)"
# Unless you are root, restrict to processes from the caller.
[ "$my_id" = root ] || ps_options="$ps_options -u $my_id"

# Many systems provide pkill/pgrep. If not this will be used as substitute.
pkill_fn()
{
    local pid_list signal program script
    script="$(basename "$0")"
    signal="$1"
    program="$2"
    # shellcheck disable=2086
    # shellcheck disable=2009
    # Get the list of PIDs. Be careful to exclude grep and the script itself
    pid_list="$(ps $ps_options | grep "$program" | grep -v -w "$script" |
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

# Commented the following, since we actually want to match processes
# by partial name, not whole names. Example: "/bin/java"
#######################################################################
# See if we have pkill available or use our version
# pkill=$(type -p pkill)
# [ -z "$pkill" ] && pkill=pkill_fn
#######################################################################

killprocs()
{
    count=0
    if pkill_fn -TERM "$1"
    then
        # Wait for up to 10 seconds for the process(es) to exit.
        while ((count<10))
        do
            pkill_fn -0 "$1" || return
            sleep 1
            ((count+=1))
        done
        pkill_fn -KILL "$1"
    fi
}

killprocs /bin/java
# killprocs "$1" # Uncomment this if you want to kill other processes than Java