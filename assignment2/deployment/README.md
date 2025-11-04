# Assignment 2 Deployment Guide

Deployment scripts and configuration for AWS infrastructure.

## Contents

- `SETUP_ALL.sh` - One-command deployment for all components
- `setup-rabbitmq.sh` - RabbitMQ installation and queue setup
- `setup-consumer.sh` - Consumer application deployment
- `deploy-all-servers.sh` - Deploy server-v2 to 4 EC2 instances
- `restart-all-tomcat.sh` - Restart all Tomcat servers
- `ALB_CONFIGURATION.md` - Application Load Balancer configuration

## Quick Start

### One-Command Deployment

```bash
./SETUP_ALL.sh
```

This will deploy:
1. RabbitMQ server with exchange and queues
2. Consumer application
3. All 4 server instances

### Manual Deployment

If you prefer step-by-step:

```bash
# 1. Deploy RabbitMQ
./setup-rabbitmq.sh

# 2. Deploy Consumer
./setup-consumer.sh

# 3. Deploy Servers
./deploy-all-servers.sh
```

### Verify Installation

Access RabbitMQ Management Console:
- URL: http://54.245.205.40:15672
- Username: guest
- Password: guest

Check for:
- 1 Exchange: chat.exchange (type: topic)
- 21 Queues: room.1-20 + chat.dlq
- 20 Bindings with routing keys

## System Architecture

```
Client → ALB → [4 Servers] → RabbitMQ → Consumer → WebSocket Broadcast
```

**Deployed Components:**
- RabbitMQ: 54.245.205.40
- Consumer: 54.70.61.198
- Server 1-4: 44.254.79.143, 50.112.195.157, 54.214.123.172, 54.190.115.9
- ALB: cs6650-alb-631563720.us-west-2.elb.amazonaws.com

## Security Group Requirements

Ensure your EC2 Security Group allows inbound traffic on:
- Port 22 (SSH)
- Port 5672 (RabbitMQ AMQP)
- Port 15672 (RabbitMQ Management Console)

## Architecture

```
Client --> ALB --> [Server Instances] --> RabbitMQ (EC2) --> Consumer --> Broadcast
```

## Testing

### Test RabbitMQ Connection

```bash
# From local machine
telnet YOUR-EC2-IP 5672

# Test management console
curl http://YOUR-EC2-IP:15672
```

### Test Consumer

```bash
# On local machine
export RABBITMQ_HOST=YOUR-EC2-IP
cd ../consumer
java -jar target/chat-consumer.jar
```

## Troubleshooting

### Cannot connect to RabbitMQ

1. Check Security Group allows port 5672
2. Verify RabbitMQ container is running: `sudo docker ps | grep rabbitmq`
3. Check RabbitMQ logs: `sudo docker logs rabbitmq`

### Queues not found

Run the queue setup script:
```bash
bash setup-rabbitmq-queues.sh
```

### Connection refused

1. Verify EC2 instance is running
2. Check Security Group settings
3. Ensure RabbitMQ is listening on 0.0.0.0:5672

## Next Steps

After RabbitMQ deployment:
1. Deploy server-v2 to EC2 instances
2. Deploy consumer application
3. Configure Application Load Balancer
4. Run performance tests

## Reference

- EC2 Public IP: [Your EC2 IP]
- RabbitMQ AMQP: YOUR-EC2-IP:5672
- RabbitMQ Management: http://YOUR-EC2-IP:15672
- Default Credentials: guest/guest
