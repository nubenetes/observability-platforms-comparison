# Advanced Vendor-Neutral OpenTelemetry Gateway Architecture

[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Collector_v0.108%2B-blue.svg?logo=opentelemetry&logoColor=white)](https://opentelemetry.io/)
[![CNCF](https://img.shields.io/badge/CNCF-Graduated-238636.svg?logo=cncf&logoColor=white)](https://www.cncf.io/)
[![OpenShift](https://img.shields.io/badge/OpenShift-4.18%2B-EE0000.svg?logo=redhat&logoColor=white)](https://www.redhat.com/)

This directory provides advanced configurations for deploying a vendor-neutral **OpenTelemetry Collector Gateway** on Red Hat OpenShift 4.18+, demonstrating unified collection across **Apache Kafka**, **Microsoft SQL Server**, and containerized Java microservices.

---

## 🏗️ Architectural Overview

```mermaid
graph TD
    subgraph "Application Layer"
        JavaApp["Java Microservices<br/>(OTel Java Agent / W3C TraceContext)"]
    end

    subgraph "OpenShift Cluster: OpenTelemetry Gateway"
        OTelGW["OpenTelemetry Collector Gateway<br/>(StatefulSet / Horizontal Pod Autoscaler)"]
        
        subgraph "Internal Processing Pipelines"
            MemLimiter["Memory Limiter Processor"]
            BatchProc["Batch Processor"]
            TailSampler["Tail-Based Sampling Processor<br/>(100% Errors & Latency > 1s)"]
        end

        OTelGW --> MemLimiter --> TailSampler --> BatchProc
    end

    subgraph "External Enterprise Infrastructure"
        Kafka["Apache Kafka Cluster<br/>(Scraped via kafkametrics receiver)"]
        MSSQL["Microsoft SQL Server<br/>(Scraped via sqlquery receiver)"]
    end

    subgraph "Pluggable Storage Backends"
        BackendA["Self-Hosted Dynatrace / Instana"]
        BackendB["Elasticsearch (ECK)"]
        BackendC["Grafana Tempo & Mimir"]
    end

    JavaApp -->|OTLP gRPC (Port 4317)| OTelGW
    Kafka -.->|JMX & Metadata Scraping| OTelGW
    MSSQL -.->|DMV SQL Query Polling| OTelGW

    BatchProc -->|Export OTLP| BackendA
    BatchProc -->|Export OTLP| BackendB
    BatchProc -->|Export OTLP| BackendC
```

---

## 🔑 Key Architectural Capabilities

### 1. Vendor-Neutral Telemetry Abstraction
The OpenTelemetry Collector insulates application developers from underlying vendor churn:
- Applications emit telemetry using the open-standard **OpenTelemetry Protocol (OTLP)**.
- If the enterprise migrates between vendors (e.g., from an open-source evaluation to Dynatrace or Instana), zero application code or container images need to be changed. Only the collector's `exporters` block is modified.

### 2. Intelligent Tail-Based Sampling
Traditional head-based sampling drops 95% of traces randomly at the root request, meaning rare production errors or slow transactions are often lost.
- The OTel Collector Gateway buffers complete transaction traces across microservices.
- **Decision Engine**: Retains 100% of traces containing HTTP 5xx errors or spans exceeding 1,000ms latency, while sampling standard successful 200 OK requests at 1%.

### 3. Integrated Kafka & SQL Server Receivers
- **`kafkametrics` Receiver**: Polls Kafka brokers for active partition counts, under-replicated status, and consumer group offset lag.
- **`sqlquery` Receiver**: Periodically executes queries against SQL Server's `sys.dm_os_wait_stats` to expose wait-state bottlenecks directly into Prometheus/Mimir without requiring external exporters.

---

## 🚀 Deployment Instructions

### Step 1: Install OpenTelemetry Operator on OpenShift
```bash
oc create namespace observability-system
oc apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml
```

### Step 2: Deploy Hybrid Collector Manifest
```bash
oc apply -f otel-collector-kafka-sql.yaml
```

### Step 3: Verify OTLP Pipeline Operation
Send a test trace to the collector and verify processing:
```bash
oc logs -n observability-system -l app.kubernetes.io/name=otel-collector-hybrid --tail=50
```
