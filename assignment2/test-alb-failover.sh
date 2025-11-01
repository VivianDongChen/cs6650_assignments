#!/bin/bash
# Part 3.4.3: Test ALB Failover Scenario
# Stop one server and verify ALB detects it as unhealthy

set -e

echo "================================================"
echo "Part 3.4.3: Testing ALB Failover Scenario"
echo "================================================"
echo ""

TEST_SERVER="34.212.226.25"
echo "Test server: $TEST_SERVER (Server 1)"
echo ""

echo "Step 1: Check current server status"
echo "----------------------------------------"
echo "Checking if Tomcat is running..."
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$TEST_SERVER "ps aux | grep tomcat | grep -v grep | wc -l" | xargs echo "Tomcat processes: "
echo ""

echo "Step 2: Stop Tomcat on test server"
echo "----------------------------------------"
echo "Stopping Tomcat on $TEST_SERVER..."
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$TEST_SERVER "sudo systemctl stop tomcat"
echo "Tomcat stopped"
echo ""

echo "Step 3: Wait for health check to detect failure"
echo "----------------------------------------"
echo "Waiting 90 seconds for ALB health checks to fail..."
echo "(Health check interval: 30s, Unhealthy threshold: 3)"
echo "Expected time: 30s * 3 = 90s"
for i in {90..1}; do
    echo -ne "\rTime remaining: ${i}s  "
    sleep 1
done
echo ""
echo ""

echo "Step 4: Verify server is marked unhealthy"
echo "----------------------------------------"
echo "Please check AWS Console:"
echo "  1. Go to EC2 > Target Groups > cs6650-server-tg"
echo "  2. Click 'Targets' tab"
echo "  3. Verify that $TEST_SERVER is marked as 'Unhealthy'"
echo ""
read -p "Press Enter after confirming the server is unhealthy..."

echo ""
echo "Step 5: Restart Tomcat on test server"
echo "----------------------------------------"
echo "Restarting Tomcat on $TEST_SERVER..."
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$TEST_SERVER "sudo systemctl start tomcat"
echo "Tomcat restarted"
echo ""

echo "Step 6: Wait for health check to pass"
echo "----------------------------------------"
echo "Waiting 60 seconds for ALB health checks to pass..."
echo "(Health check interval: 30s, Healthy threshold: 2)"
echo "Expected time: 30s * 2 = 60s"
for i in {60..1}; do
    echo -ne "\rTime remaining: ${i}s  "
    sleep 1
done
echo ""
echo ""

echo "Step 7: Verify server is back to healthy"
echo "----------------------------------------"
echo "Checking Tomcat status..."
ssh -i /Users/chendong/Desktop/6650/cs6650-hw2-key.pem -o StrictHostKeyChecking=no ec2-user@$TEST_SERVER "ps aux | grep tomcat | grep -v grep | wc -l" | xargs echo "Tomcat processes: "
echo ""
echo "Please check AWS Console to verify $TEST_SERVER is 'Healthy' again"
echo ""

echo "================================================"
echo "Failover Test Complete!"
echo "================================================"
echo "Results to verify:"
echo "  1. Server became 'Unhealthy' when Tomcat stopped"
echo "  2. ALB stopped routing traffic to unhealthy server"
echo "  3. Server became 'Healthy' after Tomcat restarted"
echo "  4. ALB resumed routing traffic to the server"
