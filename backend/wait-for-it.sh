#!/bin/sh
# wait-for-it.sh

set -e

TIMEOUT=15
QUIET=0
HOST=""
PORT=""

echo_err() {
    if [ "$QUIET" -ne 1 ]; then echo "$@" 1>&2; fi
}

usage() {
    exitcode="$1"
    cat << USAGE >&2
Usage:
    $cmdname host:port [-t timeout] [-- command args]
    -q | --quiet                        Do not output any status messages
    -t TIMEOUT | --timeout=timeout      Timeout in seconds, zero for no timeout
    -- COMMAND ARGS                     Execute command with args after the test finishes
USAGE
    exit "$exitcode"
}

wait_for() {
    if [ "$TIMEOUT" -gt 0 ]; then
        echo_err "$cmdname: waiting $TIMEOUT seconds for $HOST:$PORT"
    else
        echo_err "$cmdname: waiting for $HOST:$PORT without a timeout"
    fi
    start_ts=$(date +%s)
    while :
    do
        if nc -z "$HOST" "$PORT" >/dev/null 2>&1; then
            end_ts=$(date +%s)
            echo_err "$cmdname: $HOST:$PORT is available after $((end_ts - start_ts)) seconds"
            break
        fi
        sleep 1
    done
    return 0
}

wait_for_wrapper() {
    # In order to support SIGINT during timeout: http://unix.stackexchange.com/a/57692
    if [ "$QUIET" -eq 1 ]; then
        timeout $TIMEOUT "$0" -q -t $TIMEOUT "$HOST:$PORT" -- "$@" &
    else
        timeout $TIMEOUT "$0" -t $TIMEOUT "$HOST:$PORT" -- "$@" &
    fi
    PID=$!
    trap "kill -INT -$PID" INT
    wait $PID
    return $?
}

# process arguments
while [ $# -gt 0 ]
do
    case "$1" in
        *:* )
        HOST=$(printf "%s\n" "$1"| cut -d : -f 1)
        PORT=$(printf "%s\n" "$1"| cut -d : -f 2)
        shift 1
        ;;
        -q | --quiet)
        QUIET=1
        shift 1
        ;;
        -t)
        TIMEOUT="$2"
        if [ "$TIMEOUT" = "" ]; then break; fi
        shift 2
        ;;
        --timeout=*)
        TIMEOUT="${1#*=}"
        shift 1
        ;;
        --)
        shift
        break
        ;;
        --help)
        usage 0
        ;;
        *)
        echo_err "Unknown argument: $1"
        usage 1
        ;;
    esac
done

if [ "$HOST" = "" ] || [ "$PORT" = "" ]; then
    echo_err "Error: you need to provide a host and port to test."
    usage 2
fi

wait_for "$@"
