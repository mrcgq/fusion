#!/bin/bash

DIR="$(dirname "$0")"
PIDFILE="${DIR}/ctbrec.pid"
JAVA=java

terminate_ctbrec() {
    echo "Caught SIGTERM signal"
    kill -15 $(cat "${PIDFILE}")
}

start() {
    trap terminate_ctbrec SIGTERM
    trap terminate_ctbrec SIGINT

    USERDIR="$(pwd)"
    cd "${DIR}"

    # start ctbrec
    $JAVA -version
    $JAVA -Xmx256m -cp "${DIR}:${DIR}/${name.final}.jar" -Dfile.encoding=utf-8 -Dctbrec.config=server.json ctbrec.recorder.server.HttpServer &

    # write a pid file
    echo $! > "${PIDFILE}"

    # wait for the process to terminate and delete the PID file
    wait $(cat "${PIDFILE}")
    rm "${PIDFILE}"

    cd "${USERDIR}"
}

stop() {
    if [ -e "${PIDFILE}" ]; then
        PID=$(cat "${PIDFILE}")
        echo "Sending TERM signal"
        kill $PID
        echo -n "Waiting for ctbrec to terminate..."
        tail --pid=$PID -f /dev/null
        echo "done"
        if [ -e "${PIDFILE}" ]; then
            rm "${PIDFILE}"
        fi
    else
        echo "PID file not found"
    fi
}

status() {
    if [ -e "${PIDFILE}" ]; then
        echo "running"
    else
        echo "stopped"
    fi
}

case "$1" in
start)
    start &
    ;;
stop)
    stop
    ;;
status)
    status
    ;;
*)
    echo "Usage: $0 {start|stop|status}"
    exit 1
    ;;
esac