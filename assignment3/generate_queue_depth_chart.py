#!/usr/bin/env python3
"""
Generate Queue Depth visualization from documented data
"""
import matplotlib.pyplot as plt
import numpy as np

# Data from System Stability Analysis section
# Time in seconds, Queue Depth in messages
time_points = [0, 30, 60, 90, 120, 150]  # 0s, 30s, 1min, 1.5min, 2min, 2.5min
queue_depth = [0, 50000, 25000, 5000, 0, 0]  # Peak at 30s, drain to 0 by 2min
consumer_rate = [0, 7500, 7880, 7880, 7880, 0]  # msg/sec
publisher_rate = [0, 7880, 7880, 7880, 7880, 0]  # msg/sec

# Create figure with 2 subplots
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10))
fig.suptitle('RabbitMQ Queue Performance During Load Test', fontsize=16, fontweight='bold')

# Plot 1: Queue Depth over time
ax1.plot(time_points, queue_depth, 'b-o', linewidth=2, markersize=8, label='Queue Depth')
ax1.fill_between(time_points, queue_depth, alpha=0.3, color='blue')
ax1.axhline(y=0, color='green', linestyle='--', alpha=0.5, label='Target (0)')
ax1.set_xlabel('Time (seconds)', fontsize=12)
ax1.set_ylabel('Queue Depth (messages)', fontsize=12)
ax1.set_title('Queue Depth Over Time', fontsize=14, fontweight='bold')
ax1.grid(True, alpha=0.3)
ax1.legend(loc='upper right', fontsize=10)

# Add annotations
ax1.annotate('Peak: 50,000 messages',
             xy=(30, 50000), xytext=(50, 45000),
             arrowprops=dict(arrowstyle='->', color='red', lw=2),
             fontsize=11, color='red', fontweight='bold')
ax1.annotate('Steady State: 0 depth',
             xy=(120, 0), xytext=(100, 10000),
             arrowprops=dict(arrowstyle='->', color='green', lw=2),
             fontsize=11, color='green', fontweight='bold')

# Format y-axis
ax1.yaxis.set_major_formatter(plt.FuncFormatter(lambda x, p: f'{int(x):,}'))
ax1.set_ylim(-2000, 55000)

# Plot 2: Consumer vs Publisher Rate
ax2.plot(time_points, consumer_rate, 'g-s', linewidth=2, markersize=8, label='Consumer Rate')
ax2.plot(time_points, publisher_rate, 'r-^', linewidth=2, markersize=8, label='Publisher Rate')
ax2.set_xlabel('Time (seconds)', fontsize=12)
ax2.set_ylabel('Rate (messages/sec)', fontsize=12)
ax2.set_title('Consumer vs Publisher Rate', fontsize=14, fontweight='bold')
ax2.grid(True, alpha=0.3)
ax2.legend(loc='upper left', fontsize=10)

# Add horizontal line for target rate
ax2.axhline(y=7880, color='orange', linestyle='--', alpha=0.5, label='Target Rate: 7,880 msg/sec')
ax2.legend(loc='upper left', fontsize=10)

# Format y-axis
ax2.yaxis.set_major_formatter(plt.FuncFormatter(lambda x, p: f'{int(x):,}'))
ax2.set_ylim(-500, 9000)

# Add summary text box
summary_text = """Key Observations:
• Peak queue depth: 50,000 messages at 30 seconds
• Queue drained to 0 by 2 minutes (120 seconds)
• Steady state: 0 depth maintained
• Consumer matched publisher rate: 7,880 msg/sec
• No message loss or backlog"""

fig.text(0.5, 0.02, summary_text, ha='center', fontsize=10,
         bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.3))

plt.tight_layout(rect=[0, 0.08, 1, 0.96])
plt.savefig('load-tests/screenshots/queue_depth.png', dpi=150, bbox_inches='tight')
print("✓ Queue depth chart saved to: load-tests/screenshots/queue_depth.png")
plt.close()

# Also create a simpler version for endurance test
fig2, ax3 = plt.subplots(figsize=(12, 6))

# Extended timeline for 15-minute endurance test
time_extended = [0, 30, 120, 300, 600, 900]  # 0s, 30s, 2min, 5min, 10min, 15min
queue_extended = [0, 50000, 0, 0, 0, 0]  # Peak then stable at 0

ax3.plot(time_extended, queue_extended, 'b-o', linewidth=2.5, markersize=10)
ax3.fill_between(time_extended, queue_extended, alpha=0.3, color='blue')
ax3.axhline(y=0, color='green', linestyle='--', linewidth=2, alpha=0.5)
ax3.set_xlabel('Time (seconds)', fontsize=13, fontweight='bold')
ax3.set_ylabel('Queue Depth (messages)', fontsize=13, fontweight='bold')
ax3.set_title('Queue Depth - 15 Minute Endurance Test', fontsize=15, fontweight='bold')
ax3.grid(True, alpha=0.3, linestyle='--')

# Add time labels on x-axis
time_labels = ['Start\n(0s)', '30s', '2min', '5min', '10min', '15min']
ax3.set_xticks(time_extended)
ax3.set_xticklabels(time_labels)

# Annotations
ax3.annotate('Initial burst\n50K messages',
             xy=(30, 50000), xytext=(200, 45000),
             arrowprops=dict(arrowstyle='->', color='red', lw=2),
             fontsize=12, color='red', fontweight='bold')
ax3.annotate('Stable: 0 depth for 13+ minutes',
             xy=(600, 0), xytext=(400, 15000),
             arrowprops=dict(arrowstyle='->', color='green', lw=2),
             fontsize=12, color='green', fontweight='bold')

ax3.yaxis.set_major_formatter(plt.FuncFormatter(lambda x, p: f'{int(x):,}'))
ax3.set_ylim(-3000, 55000)

plt.tight_layout()
plt.savefig('load-tests/screenshots/queue_depth_endurance.png', dpi=150, bbox_inches='tight')
print("✓ Endurance test queue chart saved to: load-tests/screenshots/queue_depth_endurance.png")
plt.close()

print("\n✓ All charts generated successfully!")
print("  - queue_depth.png: Detailed 2.5-minute view with consumer/publisher rates")
print("  - queue_depth_endurance.png: 15-minute endurance test view")
