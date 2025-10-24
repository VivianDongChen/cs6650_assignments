#!/usr/bin/env python3
"""
Visualizes throughput over time from the throughput CSV file.
Generates a line chart showing messages per second in 10-second intervals.

Usage:
    python3 visualize_throughput.py throughput.csv

Requirements:
    pip install matplotlib pandas
"""

import sys
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path


def plot_throughput(csv_file, output_file=None):
    """
    Creates a throughput visualization from CSV data.

    Args:
        csv_file: Path to the throughput CSV file
        output_file: Optional output file path (default: throughput_chart.png)
    """
    # Read CSV data
    df = pd.read_csv(csv_file)

    # Validate required columns
    required_cols = ['startTime', 'endTime', 'messageCount', 'messagesPerSecond']
    if not all(col in df.columns for col in required_cols):
        raise ValueError(f"CSV must contain columns: {required_cols}")

    # Calculate mid-point time for x-axis (better visualization)
    df['midTime'] = (df['startTime'] + df['endTime']) / 2

    # Create figure with larger size for better readability
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

    # Plot 1: Throughput over time
    ax1.plot(df['midTime'], df['messagesPerSecond'],
             marker='o', linewidth=2, markersize=4, color='#2E86AB')
    ax1.set_xlabel('Time (seconds)', fontsize=12)
    ax1.set_ylabel('Throughput (messages/second)', fontsize=12)
    ax1.set_title('Throughput Over Time (10-second intervals)', fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    ax1.axhline(y=df['messagesPerSecond'].mean(), color='red',
                linestyle='--', label=f'Mean: {df["messagesPerSecond"].mean():.2f} msg/s')
    ax1.legend()

    # Plot 2: Cumulative message count
    df['cumulativeMessages'] = df['messageCount'].cumsum()
    ax2.plot(df['midTime'], df['cumulativeMessages'],
             marker='s', linewidth=2, markersize=4, color='#A23B72')
    ax2.set_xlabel('Time (seconds)', fontsize=12)
    ax2.set_ylabel('Cumulative Messages', fontsize=12)
    ax2.set_title('Cumulative Message Count Over Time', fontsize=14, fontweight='bold')
    ax2.grid(True, alpha=0.3)

    # Add summary statistics
    total_messages = df['messageCount'].sum()
    total_time = df['endTime'].max()
    avg_throughput = total_messages / total_time

    summary_text = f"Total Messages: {total_messages:,}\n"
    summary_text += f"Total Time: {total_time:.0f}s\n"
    summary_text += f"Avg Throughput: {avg_throughput:.2f} msg/s\n"
    summary_text += f"Peak Throughput: {df['messagesPerSecond'].max():.2f} msg/s\n"
    summary_text += f"Min Throughput: {df['messagesPerSecond'].min():.2f} msg/s"

    fig.text(0.15, 0.02, summary_text, fontsize=10,
             bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.3))

    plt.tight_layout(rect=[0, 0.08, 1, 1])

    # Save or display
    if output_file is None:
        output_file = Path(csv_file).parent / 'throughput_chart.png'

    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    print(f"✓ Chart saved to: {output_file}")

    # Print statistics
    print("\n=== Throughput Statistics ===")
    print(f"Total Messages: {total_messages:,}")
    print(f"Total Time: {total_time:.0f} seconds")
    print(f"Average Throughput: {avg_throughput:.2f} msg/s")
    print(f"Peak Throughput: {df['messagesPerSecond'].max():.2f} msg/s (at {df.loc[df['messagesPerSecond'].idxmax(), 'startTime']:.0f}s)")
    print(f"Min Throughput: {df['messagesPerSecond'].min():.2f} msg/s (at {df.loc[df['messagesPerSecond'].idxmin(), 'startTime']:.0f}s)")
    print(f"Std Deviation: {df['messagesPerSecond'].std():.2f} msg/s")


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 visualize_throughput.py <throughput.csv> [output.png]")
        print("\nExample:")
        print("  python3 visualize_throughput.py throughput.csv")
        print("  python3 visualize_throughput.py throughput.csv my_chart.png")
        sys.exit(1)

    csv_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    if not Path(csv_file).exists():
        print(f"Error: File not found: {csv_file}")
        sys.exit(1)

    try:
        plot_throughput(csv_file, output_file)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
