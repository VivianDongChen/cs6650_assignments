# Assignment 2 Deployment Guide

This directory contains deployment scripts and configuration for AWS infrastructure.

## Contents

- `setup-rabbitmq-ec2.sh` - Automated RabbitMQ installation script for EC2
- `setup-rabbitmq-queues.sh` - Script to create exchange and queues in RabbitMQ
- `EC2_DEPLOYMENT_GUIDE.md` - Detailed deployment instructions
- `alb-config.json` - Application Load Balancer configuration (Part 3)

## Quick Start

### 1. Deploy RabbitMQ to EC2

```bash
# SSH to your EC2 instance
ssh -i your-key.pem ec2-user@YOUR-EC2-IP

# Run the installation script
bash setup-rabbitmq-ec2.sh
```

### 2. Configure Exchange and Queues

```bash
# On the same EC2 instance
bash setup-rabbitmq-queues.sh
```

### 3. Verify Installation

Access RabbitMQ Management Console:
- URL: http://YOUR-EC2-IP:15672
- Username: guest
- Password: guest

Check for:
- 1 Exchange: chat.exchange (type: topic)
- 20 Queues: room.1 through room.20
- 20 Bindings with routing keys

## Environment Variables

Configure your server and consumer applications:

```bash
export RABBITMQ_HOST=YOUR-EC2-PUBLIC-IP
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```

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
