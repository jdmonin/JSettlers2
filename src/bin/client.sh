#!/bin/sh

# Usage:
# ./client.sh   to connect to localhost, default port
# ./client.sh some.host.net   connect to some.host.net, default port
# ./client.sh some.host.net 8888  connect to some.host.net, port 8888

# xtitle "Java Settlers of Catan client"

HOST=localhost
PORT=8880

if [ ! -z $1 ]; then
	HOST=$1
	if [ ! -z $2 ]; then
		PORT=$2
	fi
fi

echo "Starting Java Settlers of Catan Client..."

java -jar JSettlers.jar $HOST $PORT
