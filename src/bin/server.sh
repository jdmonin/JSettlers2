# source this in your shell

xtitle "Java Settler's of Catan!"

PORT=8881

echo "Starting the Java Settlers of Catan Server..."

java -cp target/classes soc.server.SOCServer $PORT 10 socAdmin socAdmin &
export SERVER_PID=$!

echo
echo SERVER_PID=$SERVER_PID
echo

sleep 3

echo Starting Robots...

# AI Computers
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Hal password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Joshua password &

# Robots
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Chrighton password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Gort password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Robbie password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Twiki password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Tobor password &

# Androids
java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Bishop password &
java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Deckard password &
java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Proteus password &
java -cp target/classes soc.robot.SOCRobotClient localhost $PORT T-800 password &
#java -cp target/classes soc.robot.SOCRobotClient localhost $PORT Sonny password &
