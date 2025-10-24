# CS6650 Assignment 1 Repository

This repository contains the deliverables for CS6650 Assignment 1 (WebSocket chat server, multithreaded client, and performance analysis).

## Repository Layout

| Path | Description |
|------|-------------|
| [`server/`](server/README.md) | Tomcat-deployable WebSocket server (`/chat/{roomId}` + `/health`). |
| [`client-part1/`](client-part1/README.md) | Load-testing client implementing warmup/main phases and Little’s Law analysis. |
| [`client-part2/`](client-part2/README.md) | Instrumented client with per-message metrics, CSV exports, and throughput charting. |
| [`results/`](results/README.md) | Test evidence, EC2 runs, design document, CSVs, and charts. |

GitHub URL: https://github.com/VivianDongChen/cs6650_assignment.git

## Quick Start

```bash
# Build server WAR
cd server
mvn -q clean package

# Build basic client (Part 1)
cd ../client-part1
mvn -q clean package

# Build instrumented client (Part 2)
cd ../client-part2
mvn -q clean package
```

Deployment and execution details for each module are documented in the module READMEs.

## Deliverables

- **Design document**: [`results/design-document.md`](results/design-document.md) (export to PDF for submission).
- **Test results**: screenshots and logs in `results/`, including EC2 evidence and Part 2 performance charts.
- **CSV + chart outputs**: `results/part2-output/`.

Refer to the design document for architecture, threading model, connection strategy, and Little’s Law calculations.
