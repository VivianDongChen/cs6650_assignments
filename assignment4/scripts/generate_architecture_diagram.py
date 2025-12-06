#!/usr/bin/env python3
"""
CS6650 Assignment 4 - Detailed Architecture Diagram Generator
Includes: HTTP API flow, Tech Stack, AWS Deployment Info
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np

def create_detailed_architecture():
    fig, ax = plt.subplots(1, 1, figsize=(20, 14))
    ax.set_xlim(0, 20)
    ax.set_ylim(0, 14)
    ax.axis('off')

    # Title
    ax.text(10, 13.5, 'CS6650 Assignment 4 - System Architecture',
            fontsize=20, fontweight='bold', ha='center', va='center')
    ax.text(10, 12.9, 'with HTTP API Flow, Tech Stack & AWS Deployment',
            fontsize=12, ha='center', va='center', color='gray')

    # ========== AWS Cloud Region Background ==========
    aws_cloud = FancyBboxPatch((2.5, 1), 17, 10.5, boxstyle="round,pad=0.1",
                                facecolor='#F5F9FF', edgecolor='#FF9900', linewidth=2, linestyle='--')
    ax.add_patch(aws_cloud)
    ax.text(11, 11.2, 'AWS Cloud (us-west-2)', fontsize=11, fontweight='bold',
            color='#FF9900', ha='center')

    # ========== Component Colors ==========
    client_color = '#6BB9F0'      # Blue
    alb_color = '#F39C12'         # Orange
    server_color = '#2ECC71'      # Green
    rabbitmq_color = '#F1948A'    # Light Red
    consumer_color = '#BB8FCE'    # Purple
    db_color = '#5DADE2'          # Light Blue

    # ========== Client (Local) ==========
    client_box = FancyBboxPatch((0.3, 5.5), 2, 2.5, boxstyle="round,pad=0.05",
                                 facecolor=client_color, edgecolor='#2980B9', linewidth=2)
    ax.add_patch(client_box)
    ax.text(1.3, 7.5, 'Client', fontsize=12, fontweight='bold', ha='center', color='white')
    ax.text(1.3, 7.0, '(Local)', fontsize=9, ha='center', color='white')
    ax.text(1.3, 6.4, 'JMeter +', fontsize=8, ha='center', color='white')
    ax.text(1.3, 6.0, 'Java 11', fontsize=8, ha='center', color='white')

    # Client annotations
    ax.text(1.3, 5.0, 'Multi-threaded\n1000 users\n500K messages', fontsize=8, ha='center',
            color='#555', style='italic')

    # ========== AWS ALB ==========
    alb_box = FancyBboxPatch((3.2, 5.5), 2.2, 2.5, boxstyle="round,pad=0.05",
                              facecolor=alb_color, edgecolor='#D35400', linewidth=2)
    ax.add_patch(alb_box)
    ax.text(4.3, 7.5, 'AWS ALB', fontsize=12, fontweight='bold', ha='center', color='white')
    ax.text(4.3, 7.0, 'Load Balancer', fontsize=9, ha='center', color='white')
    ax.text(4.3, 6.3, 'Sticky Sessions', fontsize=8, ha='center', color='white')
    ax.text(4.3, 5.9, 'Health Check', fontsize=8, ha='center', color='white')

    # ALB annotations
    ax.text(4.3, 5.0, 'WebSocket Support\nPath-based Routing', fontsize=8, ha='center',
            color='#555', style='italic')

    # ALB EC2 badge
    ax.text(4.3, 8.3, 'AWS Service', fontsize=7, ha='center',
            color='white', bbox=dict(boxstyle='round', facecolor='#FF9900', edgecolor='none', pad=0.2))

    # ========== Server x4 (Stacked) ==========
    # Draw stacked servers
    for i in range(3):
        offset = i * 0.15
        server_bg = FancyBboxPatch((6.2 + offset, 5.5 - offset), 2.5, 2.5, boxstyle="round,pad=0.05",
                                    facecolor='#27AE60', edgecolor='#1E8449', linewidth=1, alpha=0.6)
        ax.add_patch(server_bg)

    server_box = FancyBboxPatch((6.65, 5.05), 2.5, 2.5, boxstyle="round,pad=0.05",
                                 facecolor=server_color, edgecolor='#1E8449', linewidth=2)
    ax.add_patch(server_box)
    ax.text(7.9, 7.0, 'Server x4', fontsize=12, fontweight='bold', ha='center', color='white')
    ax.text(7.9, 6.5, 'Tomcat 9', fontsize=9, ha='center', color='white')
    ax.text(7.9, 6.0, 'Java 11', fontsize=8, ha='center', color='white')
    ax.text(7.9, 5.6, 'WebSocket API', fontsize=8, ha='center', color='white')
    ax.text(7.9, 5.2, 'Jackson JSON', fontsize=8, ha='center', color='white')

    # Server EC2 badge
    ax.text(7.9, 8.0, 'EC2 t3.micro x4', fontsize=7, ha='center',
            color='white', bbox=dict(boxstyle='round', facecolor='#FF9900', edgecolor='none', pad=0.2))

    # Server annotations
    ax.text(7.9, 4.4, 'WebSocket Endpoint:\nws://host/chat/{roomId}\n\nValidate & Publish\nrouting_key',
            fontsize=8, ha='center', color='#555', style='italic')

    # ========== RabbitMQ ==========
    rmq_box = FancyBboxPatch((10.0, 5.5), 2.3, 2.5, boxstyle="round,pad=0.05",
                              facecolor=rabbitmq_color, edgecolor='#E74C3C', linewidth=2)
    ax.add_patch(rmq_box)
    ax.text(11.15, 7.5, 'RabbitMQ', fontsize=12, fontweight='bold', ha='center', color='white')
    ax.text(11.15, 7.0, 'Message Broker', fontsize=9, ha='center', color='white')
    ax.text(11.15, 6.4, 'AMQP 0-9-1', fontsize=8, ha='center', color='white')
    ax.text(11.15, 6.0, 'Exchange: fanout', fontsize=8, ha='center', color='white')
    ax.text(11.15, 5.65, 'Queues: room.1~20', fontsize=8, ha='center', color='white')

    # RabbitMQ EC2 badge
    ax.text(11.15, 8.3, 'EC2 t3.small', fontsize=7, ha='center',
            color='white', bbox=dict(boxstyle='round', facecolor='#FF9900', edgecolor='none', pad=0.2))

    # ========== Consumer Box (with internal components) ==========
    consumer_outer = FancyBboxPatch((13.0, 3.0), 3.5, 7.2, boxstyle="round,pad=0.1",
                                     facecolor='#F5EEF8', edgecolor='#8E44AD', linewidth=2)
    ax.add_patch(consumer_outer)
    ax.text(14.75, 9.9, 'Consumer', fontsize=14, fontweight='bold', ha='center', color='#8E44AD')

    # Consumer EC2 badge
    ax.text(14.75, 10.4, 'EC2 t3.small', fontsize=7, ha='center',
            color='white', bbox=dict(boxstyle='round', facecolor='#FF9900', edgecolor='none', pad=0.2))

    # Internal components
    components = [
        ('MessageConsumer', '20 threads', 9.0),
        ('RoomManager', 'Caffeine Cache\n(Deduplication)', 7.6),
        ('BatchWriter', '1000/batch, 500ms', 6.0),
        ('ConnPool', 'HikariCP\n50 connections', 4.4),
    ]

    for name, desc, y_pos in components:
        comp_box = FancyBboxPatch((13.3, y_pos - 0.6), 3.0, 1.2, boxstyle="round,pad=0.03",
                                   facecolor=consumer_color, edgecolor='#7D3C98', linewidth=1)
        ax.add_patch(comp_box)
        ax.text(14.8, y_pos + 0.2, name, fontsize=10, fontweight='bold', ha='center', color='white')
        ax.text(14.8, y_pos - 0.25, desc, fontsize=7, ha='center', color='white')

    # Consumer tech stack annotation
    ax.text(14.75, 3.2, 'Java 11 | Jetty 11\nSlf4j + Logback', fontsize=8, ha='center',
            color='#555', style='italic')

    # ========== PostgreSQL ==========
    db_box = FancyBboxPatch((17.2, 5.5), 2.3, 2.5, boxstyle="round,pad=0.05",
                             facecolor=db_color, edgecolor='#2980B9', linewidth=2)
    ax.add_patch(db_box)
    ax.text(18.35, 7.5, 'PostgreSQL', fontsize=12, fontweight='bold', ha='center', color='white')
    ax.text(18.35, 7.0, 'Database', fontsize=9, ha='center', color='white')
    ax.text(18.35, 6.4, 'PostgreSQL 16', fontsize=8, ha='center', color='white')
    ax.text(18.35, 6.0, 'messages table', fontsize=8, ha='center', color='white')
    ax.text(18.35, 5.65, '5 indexes', fontsize=8, ha='center', color='white')

    # PostgreSQL RDS badge
    ax.text(18.35, 8.3, 'AWS RDS', fontsize=7, ha='center',
            color='white', bbox=dict(boxstyle='round', facecolor='#FF9900', edgecolor='none', pad=0.2))

    # DB annotations
    ax.text(18.35, 5.0, 'Covering Index\nBRIN Index\nMaterialized Views', fontsize=8, ha='center',
            color='#555', style='italic')

    # ========== Arrows (Message Flow) ==========
    arrow_style = dict(arrowstyle='->', color='#333', lw=2)

    # Client -> ALB
    ax.annotate('', xy=(3.2, 6.75), xytext=(2.3, 6.75), arrowprops=arrow_style)
    ax.text(2.75, 7.1, 'HTTP/WS', fontsize=7, ha='center', color='#333')

    # ALB -> Server
    ax.annotate('', xy=(6.2, 6.3), xytext=(5.4, 6.75), arrowprops=arrow_style)
    ax.text(5.8, 7.1, 'WebSocket', fontsize=7, ha='center', color='#333')

    # Server -> RabbitMQ
    ax.annotate('', xy=(10.0, 6.75), xytext=(9.15, 6.3), arrowprops=arrow_style)
    ax.text(9.6, 7.1, 'AMQP Publish', fontsize=7, ha='center', color='#333')

    # RabbitMQ -> Consumer
    ax.annotate('', xy=(13.3, 8.5), xytext=(12.3, 6.75), arrowprops=arrow_style)
    ax.text(12.4, 8.0, 'Consume', fontsize=7, ha='center', color='#333')

    # Consumer internal arrows
    internal_arrow = dict(arrowstyle='->', color='#666', lw=1.5)
    ax.annotate('', xy=(14.8, 8.3), xytext=(14.8, 8.6), arrowprops=internal_arrow)
    ax.annotate('', xy=(14.8, 6.7), xytext=(14.8, 7.0), arrowprops=internal_arrow)
    ax.annotate('', xy=(14.8, 5.1), xytext=(14.8, 5.4), arrowprops=internal_arrow)

    # Consumer -> PostgreSQL
    ax.annotate('', xy=(17.2, 6.75), xytext=(16.3, 4.9), arrowprops=arrow_style)
    ax.text(16.9, 6.2, 'Batch INSERT', fontsize=7, ha='center', color='#333')

    # ========== WebSocket Broadcast (Dashed) ==========
    broadcast_style = dict(arrowstyle='->', color='#9B59B6', lw=2, linestyle='dashed')
    # Consumer -> Client (broadcast)
    ax.annotate('', xy=(1.3, 8.0), xytext=(13.0, 9.5),
                arrowprops=dict(arrowstyle='->', color='#9B59B6', lw=2,
                               connectionstyle="arc3,rad=0.2", linestyle='dashed'))
    ax.text(7, 10.2, 'WebSocket Broadcast (via RoomManager)', fontsize=9, ha='center',
            color='#9B59B6', style='italic')

    # ========== HTTP API Section ==========
    api_box = FancyBboxPatch((0.5, 0.5), 7.5, 2.3, boxstyle="round,pad=0.1",
                              facecolor='#FDEBD0', edgecolor='#E67E22', linewidth=1)
    ax.add_patch(api_box)
    ax.text(4.25, 2.5, 'HTTP API Endpoints', fontsize=11, fontweight='bold', ha='center', color='#E67E22')

    api_text = """Server (Tomcat):
  • WS  ws://alb/chat/{roomId}  - WebSocket chat connection
  • GET /health                 - Health check for ALB

Consumer (Jetty port 8080):
  • GET /metrics               - Performance metrics (JSON)
  • GET /health                - Consumer health check"""
    ax.text(0.7, 2.1, api_text, fontsize=8, ha='left', va='top', family='monospace')

    # ========== Tech Stack Legend ==========
    legend_box = FancyBboxPatch((8.5, 0.5), 5.5, 2.3, boxstyle="round,pad=0.1",
                                 facecolor='#E8F8F5', edgecolor='#1ABC9C', linewidth=1)
    ax.add_patch(legend_box)
    ax.text(11.25, 2.5, 'Tech Stack Summary', fontsize=11, fontweight='bold', ha='center', color='#1ABC9C')

    tech_text = """• Server:   Java 11, Tomcat 9, WebSocket JSR-356, Jackson, RabbitMQ Client
• Consumer: Java 11, Jetty 11, HikariCP, Caffeine, PostgreSQL JDBC
• Queue:    RabbitMQ 3.x (AMQP 0-9-1), Fanout Exchange
• Database: PostgreSQL 16, BRIN Index, Materialized Views"""
    ax.text(8.7, 2.1, tech_text, fontsize=8, ha='left', va='top', family='monospace')

    # ========== Components Legend ==========
    comp_box = FancyBboxPatch((14.5, 0.5), 5.0, 2.3, boxstyle="round,pad=0.1",
                               facecolor='#FDEDEC', edgecolor='#E74C3C', linewidth=1)
    ax.add_patch(comp_box)
    ax.text(17, 2.5, 'Deployment Summary', fontsize=11, fontweight='bold', ha='center', color='#E74C3C')

    deploy_text = """• 4 Server instances (EC2 t3.micro)
• 1 RabbitMQ instance (EC2 t3.small)
• 1 Consumer instance (EC2 t3.small)
• 1 PostgreSQL (RDS db.t3.micro)
• 1 Application Load Balancer (AWS ALB)"""
    ax.text(14.7, 2.1, deploy_text, fontsize=8, ha='left', va='top', family='monospace')

    plt.tight_layout()
    return fig

if __name__ == '__main__':
    fig = create_detailed_architecture()
    output_path = '/Users/chendong/Desktop/6650/cs6650_assignments/assignment4/screenshots/architecture-diagram-detailed.png'
    fig.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='white')
    print(f'Architecture diagram saved to: {output_path}')
    plt.close()
