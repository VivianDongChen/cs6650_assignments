#!/usr/bin/env python3
"""
CS6650 Assignment 3 - System Architecture Diagram
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import matplotlib.lines as mlines

# Set up the figure
fig, ax = plt.subplots(1, 1, figsize=(16, 10))
ax.set_xlim(0, 16)
ax.set_ylim(0, 10)
ax.set_aspect('equal')
ax.axis('off')

# Colors
colors = {
    'client': '#4A90D9',      # Blue
    'alb': '#FF9900',         # AWS Orange
    'server': '#48BB78',      # Green
    'rabbitmq': '#FF6B35',    # RabbitMQ Orange
    'consumer': '#9F7AEA',    # Purple
    'database': '#3182CE',    # DB Blue
    'arrow': '#2D3748',       # Dark gray
    'text': '#1A202C',        # Almost black
    'subtext': '#718096',     # Gray
}

def draw_box(ax, x, y, width, height, color, label, sublabel=None, rounded=True):
    """Draw a rounded rectangle with label"""
    if rounded:
        box = FancyBboxPatch((x, y), width, height,
                             boxstyle="round,pad=0.02,rounding_size=0.15",
                             facecolor=color, edgecolor='white', linewidth=2, alpha=0.9)
    else:
        box = FancyBboxPatch((x, y), width, height,
                             facecolor=color, edgecolor='white', linewidth=2, alpha=0.9)
    ax.add_patch(box)

    # Main label
    ax.text(x + width/2, y + height/2 + (0.15 if sublabel else 0), label,
            ha='center', va='center', fontsize=11, fontweight='bold', color='white')

    # Sublabel
    if sublabel:
        ax.text(x + width/2, y + height/2 - 0.2, sublabel,
                ha='center', va='center', fontsize=8, color='white', alpha=0.9)

def draw_arrow(ax, start, end, color='#2D3748', style='->', linewidth=2, connectionstyle="arc3,rad=0"):
    """Draw an arrow between two points"""
    arrow = FancyArrowPatch(start, end,
                            arrowstyle=style,
                            color=color,
                            linewidth=linewidth,
                            connectionstyle=connectionstyle,
                            mutation_scale=15)
    ax.add_patch(arrow)

def draw_dashed_arrow(ax, start, end, color='#718096'):
    """Draw a dashed arrow"""
    ax.annotate('', xy=end, xytext=start,
                arrowprops=dict(arrowstyle='->', color=color,
                               linestyle='dashed', linewidth=1.5))

# Title
ax.text(8, 9.5, 'CS6650 Assignment 3 - System Architecture',
        ha='center', va='center', fontsize=18, fontweight='bold', color=colors['text'])

# ============ Draw Components ============

# 1. Client (left)
draw_box(ax, 0.5, 4, 1.8, 2, colors['client'], 'Client', '(Local)')
ax.text(1.4, 3.5, 'Multi-threaded\n500K messages', ha='center', fontsize=7, color=colors['subtext'])

# 2. AWS ALB
draw_box(ax, 3, 4, 1.8, 2, colors['alb'], 'AWS ALB', 'Load Balancer')
ax.text(3.9, 3.5, 'Sticky Sessions\nHealth Check', ha='center', fontsize=7, color=colors['subtext'])

# 3. Servers (x4)
for i, offset in enumerate([0, 0.6, 1.2, 1.8]):
    alpha = 0.9 - i * 0.15
    box = FancyBboxPatch((5.5 + offset * 0.15, 4.3 - offset * 0.15), 1.6, 1.6,
                         boxstyle="round,pad=0.02,rounding_size=0.1",
                         facecolor=colors['server'], edgecolor='white',
                         linewidth=1.5, alpha=alpha)
    ax.add_patch(box)

ax.text(6.5, 5.1, 'Server x4', ha='center', va='center', fontsize=11, fontweight='bold', color='white')
ax.text(6.5, 4.7, 'Tomcat', ha='center', va='center', fontsize=8, color='white', alpha=0.9)
ax.text(6.5, 3.5, 'Validate & Publish\nrouting_key', ha='center', fontsize=7, color=colors['subtext'])

# 4. RabbitMQ
draw_box(ax, 8.2, 4, 1.8, 2, colors['rabbitmq'], 'RabbitMQ', 'chat.exchange')
ax.text(9.1, 3.5, 'room.1~20\nFanout', ha='center', fontsize=7, color=colors['subtext'])

# 5. Consumer (large box with components)
consumer_x, consumer_y = 10.8, 2.5
consumer_w, consumer_h = 3.5, 5

# Consumer outer box
consumer_box = FancyBboxPatch((consumer_x, consumer_y), consumer_w, consumer_h,
                               boxstyle="round,pad=0.02,rounding_size=0.2",
                               facecolor=colors['consumer'], edgecolor='white',
                               linewidth=2, alpha=0.2)
ax.add_patch(consumer_box)
ax.text(consumer_x + consumer_w/2, consumer_y + consumer_h - 0.3, 'Consumer',
        ha='center', fontsize=12, fontweight='bold', color=colors['consumer'])

# Consumer internal components
draw_box(ax, 11, 6.2, 2, 0.7, colors['consumer'], 'MessageConsumer', '20 threads')
draw_box(ax, 11, 5.2, 2, 0.7, colors['consumer'], 'BatchWriter', '1000/batch')
draw_box(ax, 11, 4.2, 2, 0.7, colors['consumer'], 'ConnPool', 'HikariCP')
draw_box(ax, 11, 3.2, 2, 0.7, colors['consumer'], 'RoomManager', 'Broadcast')

# 6. PostgreSQL (RDS)
draw_box(ax, 14.5, 4, 1.3, 2, colors['database'], 'PostgreSQL', '(RDS)')
ax.text(15.15, 3.5, 'messages table\n5 indexes', ha='center', fontsize=7, color=colors['subtext'])

# ============ Draw Arrows (Data Flow) ============

# Client -> ALB
draw_arrow(ax, (2.3, 5), (3, 5))

# ALB -> Servers
draw_arrow(ax, (4.8, 5), (5.5, 5))

# Servers -> RabbitMQ
draw_arrow(ax, (7.3, 5), (8.2, 5))

# RabbitMQ -> Consumer
draw_arrow(ax, (10, 5), (10.8, 5))
draw_arrow(ax, (10, 5), (11, 6.55), connectionstyle="arc3,rad=-0.2")

# Consumer internal flow
ax.annotate('', xy=(12, 5.2), xytext=(12, 5.9),
            arrowprops=dict(arrowstyle='->', color='white', linewidth=1.5))
ax.annotate('', xy=(12, 4.2), xytext=(12, 4.9),
            arrowprops=dict(arrowstyle='->', color='white', linewidth=1.5))
ax.annotate('', xy=(12, 3.2), xytext=(12, 3.9),
            arrowprops=dict(arrowstyle='->', color='white', linewidth=1.5))

# ConnPool -> PostgreSQL
draw_arrow(ax, (13, 4.55), (14.5, 5))

# ============ Broadcast Flow (Dashed) ============

# RoomManager -> Client (broadcast)
ax.annotate('', xy=(1.4, 6.3), xytext=(11, 3.55),
            arrowprops=dict(arrowstyle='->', color=colors['consumer'],
                           linestyle='dashed', linewidth=1.5,
                           connectionstyle="arc3,rad=0.3"))
ax.text(6, 7.5, 'WebSocket Broadcast', ha='center', fontsize=9,
        color=colors['consumer'], style='italic')

# ============ Metrics API ============
ax.text(12, 2.3, 'Metrics API (port 8080)', ha='center', fontsize=8,
        color=colors['subtext'], style='italic')
ax.text(12, 2.0, 'GET /metrics | GET /health', ha='center', fontsize=7,
        color=colors['subtext'])

# ============ Legend ============
legend_y = 1.2
ax.text(1, legend_y, 'Legend:', fontsize=9, fontweight='bold', color=colors['text'])

# Solid arrow
ax.annotate('', xy=(3, legend_y), xytext=(2, legend_y),
            arrowprops=dict(arrowstyle='->', color=colors['arrow'], linewidth=2))
ax.text(3.3, legend_y, 'Message Flow', va='center', fontsize=8, color=colors['subtext'])

# Dashed arrow
ax.annotate('', xy=(6, legend_y), xytext=(5, legend_y),
            arrowprops=dict(arrowstyle='->', color=colors['consumer'],
                           linestyle='dashed', linewidth=1.5))
ax.text(6.3, legend_y, 'Broadcast', va='center', fontsize=8, color=colors['subtext'])

# Component count
ax.text(8.5, legend_y, 'Components: 4 Servers | 20 Consumer Threads | 50 DB Connections',
        va='center', fontsize=8, color=colors['subtext'])

# Save
plt.tight_layout()
plt.savefig('/Users/chendong/Desktop/6650/cs6650_assignments/assignment4/screenshots/architecture-diagram.png',
            dpi=150, bbox_inches='tight', facecolor='white', edgecolor='none')
plt.savefig('/Users/chendong/Desktop/6650/cs6650_assignments/assignment4/screenshots/architecture-diagram.pdf',
            bbox_inches='tight', facecolor='white', edgecolor='none')
print("Architecture diagram saved to:")
print("  - screenshots/architecture-diagram.png")
print("  - screenshots/architecture-diagram.pdf")
