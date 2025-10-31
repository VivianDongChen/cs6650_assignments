# Server Module

This module hosts the Assignment 1 WebSocket server implementation. The service exposes two endpoints:

- `/chat/{roomId}` – WebSocket endpoint that validates incoming JSON and echoes the payload with server timestamps.
- `/health` – REST endpoint returning a JSON `{status,timestamp}` for liveness checks.

## Prerequisites
- JDK 17 (or JDK 11+) – Maven compiler runs with `--release 11`.
- Maven 3.8+.
- Optional: `wscat` (`npm install -g wscat`) for WebSocket smoke tests.

## Build & Run Locally
```bash
cd server
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package

# Deploy the generated WAR to a local Tomcat installation
cp target/chat-server.war ~/apache-tomcat/webapps/
~/apache-tomcat/bin/startup.sh
```

### Local Verification
```bash
# Health endpoint
curl http://localhost:8080/chat-server/health

# WebSocket echo
wscat -c ws://localhost:8080/chat-server/chat/1
> {"userId":1,"username":"user001","message":"hello","timestamp":"2025-10-09T00:00:00Z","messageType":"TEXT"}
< {"status":"success", ...}
```

## Deploy to AWS EC2 (us-west-2)
1. Upload the WAR:
   ```bash
   scp -i ~/Desktop/6650/cs6650-hw-key.pem \
       target/chat-server.war \
       ec2-user@<EC2_PUBLIC_IP>:/tmp/
   ```
2. SSH into the instance and deploy:
   ```bash
   ssh -i ~/Desktop/6650/cs6650-hw-key.pem ec2-user@<EC2_PUBLIC_IP>
   sudo mv /tmp/chat-server.war /opt/tomcat9/webapps/
   sudo /opt/tomcat9/bin/shutdown.sh   # ignore error if Tomcat already stopped
   sudo /opt/tomcat9/bin/startup.sh
   ```
3. Verify from your machine:
   ```bash
   curl http://<EC2_PUBLIC_IP>:8080/chat-server/health
   wscat -c ws://<EC2_PUBLIC_IP>:8080/chat-server/chat/1
   ```

⚠️ Ensure the EC2 security group allows inbound TCP on ports 22 (SSH) and 8080 (Tomcat) from your IP.

## Key Source Directories
- `model/` – `ChatMessage`, `MessageType`.
- `validation/` – `MessageValidator` and `ValidationResult`.
- `handler/` – `EchoMessageHandler` for response construction.
- `ws/` – `ChatWebSocketEndpoint`.
- `web/` – `HealthServlet`.
- `resources/logback.xml` – Console logging configuration.
