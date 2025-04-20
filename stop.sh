#!/bin/bash

pid_file="java_pid"

if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    if [ -n "$pid" ]; then
        echo "Found Java process with PID: $pid"
        kill "$pid"
        if [ $? -eq 0 ]; then
            echo "Java process killed successfully."
        else
            echo "Failed to kill Java process."
        fi
    else
        echo "No PID found in $pid_file."
    fi
else
    echo "$pid_file does not exist."
fi
