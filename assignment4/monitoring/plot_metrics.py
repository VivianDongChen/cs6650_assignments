#!/usr/bin/env python3
"""
CS6650 Assignment 4 - Resource Utilization Graph Generator
生成 CPU、内存、数据库连接数的可视化图表
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import datetime
import sys
import os

def load_metrics(filepath):
    """加载 metrics.csv 文件"""
    df = pd.read_csv(filepath)
    df['datetime'] = pd.to_datetime(df['datetime'])
    return df

def plot_resource_utilization(df, output_prefix="resource_utilization"):
    """生成资源使用图表"""

    fig, axes = plt.subplots(3, 1, figsize=(12, 10))
    fig.suptitle('Resource Utilization During Load Test', fontsize=14, fontweight='bold')

    # 1. CPU 使用率
    ax1 = axes[0]
    ax1.plot(df['datetime'], df['cpu_percent'], 'b-', linewidth=1.5, label='CPU Usage')
    ax1.fill_between(df['datetime'], df['cpu_percent'], alpha=0.3)
    ax1.set_ylabel('CPU (%)', fontsize=11)
    ax1.set_title('CPU Utilization', fontsize=12)
    ax1.set_ylim(0, 100)
    ax1.axhline(y=80, color='r', linestyle='--', alpha=0.5, label='Warning Threshold (80%)')
    ax1.legend(loc='upper right')
    ax1.grid(True, alpha=0.3)

    # 2. 内存使用率
    ax2 = axes[1]
    ax2.plot(df['datetime'], df['memory_percent'], 'g-', linewidth=1.5, label='Memory Usage')
    ax2.fill_between(df['datetime'], df['memory_percent'], alpha=0.3, color='green')
    ax2.set_ylabel('Memory (%)', fontsize=11)
    ax2.set_title('Memory Utilization', fontsize=12)
    ax2.set_ylim(0, 100)
    ax2.axhline(y=90, color='r', linestyle='--', alpha=0.5, label='Warning Threshold (90%)')
    ax2.legend(loc='upper right')
    ax2.grid(True, alpha=0.3)

    # 3. 数据库连接数 (如果有数据)
    ax3 = axes[2]
    if 'db_connections' in df.columns and df['db_connections'].notna().any():
        # 将 N/A 转换为 NaN
        df['db_connections_numeric'] = pd.to_numeric(df['db_connections'], errors='coerce')
        if df['db_connections_numeric'].notna().any():
            ax3.plot(df['datetime'], df['db_connections_numeric'], 'orange', linewidth=1.5, label='DB Connections')
            ax3.fill_between(df['datetime'], df['db_connections_numeric'], alpha=0.3, color='orange')
            ax3.set_ylabel('Connections', fontsize=11)
            ax3.set_title('Database Connections', fontsize=12)
            ax3.legend(loc='upper right')
        else:
            ax3.text(0.5, 0.5, 'Database connection data not available',
                    ha='center', va='center', transform=ax3.transAxes, fontsize=12)
            ax3.set_title('Database Connections (No Data)', fontsize=12)
    else:
        # 使用 Load Average 作为替代
        ax3.plot(df['datetime'], df['load_avg_1m'], 'purple', linewidth=1.5, label='1-min Load Avg')
        ax3.fill_between(df['datetime'], df['load_avg_1m'], alpha=0.3, color='purple')
        ax3.set_ylabel('Load Average', fontsize=11)
        ax3.set_title('System Load Average (1 minute)', fontsize=12)
        ax3.legend(loc='upper right')

    ax3.grid(True, alpha=0.3)
    ax3.set_xlabel('Time', fontsize=11)

    # 格式化 x 轴时间
    for ax in axes:
        ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
        ax.xaxis.set_major_locator(mdates.AutoDateLocator())
        plt.setp(ax.xaxis.get_majorticklabels(), rotation=45)

    plt.tight_layout()

    # 保存图片
    output_file = f"{output_prefix}.png"
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    print(f"✅ Graph saved to: {output_file}")

    return output_file

def plot_comparison(metrics_files, labels, output_file="comparison.png"):
    """对比多个测试结果"""

    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('Performance Comparison: Baseline vs Optimizations', fontsize=14, fontweight='bold')

    colors = ['blue', 'green', 'red', 'orange']

    for i, (filepath, label) in enumerate(zip(metrics_files, labels)):
        if not os.path.exists(filepath):
            print(f"⚠️ File not found: {filepath}")
            continue

        df = load_metrics(filepath)
        color = colors[i % len(colors)]

        # CPU
        axes[0, 0].plot(df['datetime'], df['cpu_percent'], color=color, label=label, linewidth=1.5)

        # Memory
        axes[0, 1].plot(df['datetime'], df['memory_percent'], color=color, label=label, linewidth=1.5)

        # Load Average
        axes[1, 0].plot(df['datetime'], df['load_avg_1m'], color=color, label=label, linewidth=1.5)

    # 设置标签和标题
    axes[0, 0].set_title('CPU Utilization Comparison')
    axes[0, 0].set_ylabel('CPU (%)')
    axes[0, 0].legend()
    axes[0, 0].grid(True, alpha=0.3)

    axes[0, 1].set_title('Memory Utilization Comparison')
    axes[0, 1].set_ylabel('Memory (%)')
    axes[0, 1].legend()
    axes[0, 1].grid(True, alpha=0.3)

    axes[1, 0].set_title('System Load Comparison')
    axes[1, 0].set_ylabel('Load Average (1m)')
    axes[1, 0].legend()
    axes[1, 0].grid(True, alpha=0.3)

    # Summary Statistics
    axes[1, 1].axis('off')
    summary_text = "📊 Summary Statistics\n" + "="*40 + "\n\n"

    for filepath, label in zip(metrics_files, labels):
        if os.path.exists(filepath):
            df = load_metrics(filepath)
            summary_text += f"{label}:\n"
            summary_text += f"  • Avg CPU: {df['cpu_percent'].mean():.1f}%\n"
            summary_text += f"  • Max CPU: {df['cpu_percent'].max():.1f}%\n"
            summary_text += f"  • Avg Memory: {df['memory_percent'].mean():.1f}%\n"
            summary_text += f"  • Max Memory: {df['memory_percent'].max():.1f}%\n\n"

    axes[1, 1].text(0.1, 0.9, summary_text, transform=axes[1, 1].transAxes,
                    fontsize=10, verticalalignment='top', fontfamily='monospace',
                    bbox=dict(boxstyle='round', facecolor='lightgray', alpha=0.5))

    plt.tight_layout()
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    print(f"✅ Comparison graph saved to: {output_file}")

def main():
    if len(sys.argv) < 2:
        print("Usage:")
        print("  Single file:  python plot_metrics.py metrics.csv")
        print("  Comparison:   python plot_metrics.py --compare main.csv opt1.csv opt2.csv")
        sys.exit(1)

    if sys.argv[1] == "--compare":
        if len(sys.argv) < 4:
            print("Error: Need at least 2 files for comparison")
            sys.exit(1)

        files = sys.argv[2:]
        labels = [f"Test {i+1}" for i in range(len(files))]
        # 尝试从文件名推断标签
        for i, f in enumerate(files):
            if "main" in f.lower():
                labels[i] = "Baseline (main)"
            elif "opt1" in f.lower() or "optimization1" in f.lower():
                labels[i] = "Optimization 1"
            elif "opt2" in f.lower() or "optimization2" in f.lower():
                labels[i] = "Optimization 2"

        plot_comparison(files, labels)
    else:
        filepath = sys.argv[1]
        if not os.path.exists(filepath):
            print(f"Error: File not found: {filepath}")
            sys.exit(1)

        df = load_metrics(filepath)
        output_prefix = os.path.splitext(filepath)[0]
        plot_resource_utilization(df, output_prefix)

        # 打印统计摘要
        print("\n📊 Statistics Summary:")
        print(f"  • Duration: {df['datetime'].iloc[0]} to {df['datetime'].iloc[-1]}")
        print(f"  • CPU - Avg: {df['cpu_percent'].mean():.1f}%, Max: {df['cpu_percent'].max():.1f}%")
        print(f"  • Memory - Avg: {df['memory_percent'].mean():.1f}%, Max: {df['memory_percent'].max():.1f}%")

if __name__ == "__main__":
    main()
