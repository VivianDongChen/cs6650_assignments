#!/bin/bash
# Run a quick test to generate RabbitMQ activity for screenshots

CLIENT_JAR="/Users/chendong/Desktop/6650/cs6650_assignments/assignment1/client-part2/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar"
SERVER_URI="ws://cs6650-alb-631563720.us-west-2.elb.amazonaws.com:8080/chat-server/chat/1"

echo "======================================================================"
echo "Quick Test for RabbitMQ Screenshots"
echo "======================================================================"
echo ""
echo "This will run a 30-60 second test to generate RabbitMQ activity."
echo ""
echo "INSTRUCTIONS:"
echo "1. Open browser to: http://18.246.237.223:15672"
echo "2. Login: guest / guest"
echo "3. Go to 'Overview' tab"
echo "4. When you see message activity in the graphs, take screenshots"
echo ""
echo "Starting in 10 seconds..."
echo ""
sleep 5
echo "5..."
sleep 1
echo "4..."
sleep 1
echo "3..."
sleep 1
echo "2..."
sleep 1
echo "1..."
sleep 1
echo ""
echo "TEST STARTING NOW! Take your screenshots!"
echo ""

cd /Users/chendong/Desktop/6650/cs6650_assignments/assignment2/results

java -jar "$CLIENT_JAR" \
  --server-uri="$SERVER_URI" \
  --warmup-threads=0 \
  --main-threads=64 \
  --messages-per-thread=1000 \
  --total-messages=64000

echo ""
echo "======================================================================"
echo "Test complete!"
echo "======================================================================"
echo ""
echo "Now take your RabbitMQ screenshots:"
echo "1. Overview page (showing connection counts and graphs)"
echo "2. Queues page (showing all 20 room queues)"
echo ""
