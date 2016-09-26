#!/bin/sh

# sample server startup script: Default port, 8 bots
# To run server in background, use & when running the script

PORT=8880

echo "Starting the Java Settlers of Catan Server..."

# Params can be given on command line or jsserver.properties in current directory.
# For parameter info see README.txt or run with --help

# Max connections 30, including the 8 bots;
# startrobots=8 gives a mix of smart Robots and fast Droids

java -jar JSettlersServer.jar -Djsettlers.startrobots=8 "$@" $PORT 30
RC=$?

if [ $RC -ne 0 ]; then
	echo ""
	echo "* Server exited; return code $RC"
	# Remember: interrupt and other signals will give RC >= 128
fi
exit $RC
