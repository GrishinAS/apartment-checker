#!/bin/bash

# place this file and stop.sh one level above the project folder

cd apartment-checker

export SQL_DB_USERNAME=
export SQL_DB_PASSWORD=
export TELEGRAM_BOT_TOKEN=

./gradlew bootJar

java -Duser.timezone=PST8 -jar ./build/libs/apartment.checker-1.0.0.jar &
APP_PID=$!

echo $APP_PID > java_pid

echo App started with pid: $APP_PID
