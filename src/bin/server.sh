#!/bin/sh

# sample server startup script: Default port, 8 bots

PORT=8880

echo "Starting the Java Settlers of Catan Server..."

# Params can be given on command line or jsserver.properties in current directory.
# Max connections 30, including the 8 bots;
# startrobots=8 gives a mix of smart Robots and fast Droids

java -jar JSettlersServer.jar $PORT 30 -Djsettlers.startrobots=8  &
export SERVER_PID=$!

echo
echo SERVER_PID=$SERVER_PID
echo

