# Advanced Instana Self-Hosted Architecture on OpenShift

[![Instana](https://img.shields.io/badge/IBM-Instana_Self--Hosted-054ADA.svg?logo=ibm&logoColor=white)](https://www.instana.com/)
[![OpenShift](https://img.shields.io/badge/OpenShift-4.18%2B-EE0000.svg?logo=redhat&logoColor=white)](https://www.redhat.com/)
[![Air--Gapped](https://img.shields.io/badge/Deployment-Air--Gapped_Certified-success.svg)](docs/ARCHITECTURE_AND_AIRGAP.md)

This directory provides advanced configuration manifests and architectural documentation for running **IBM Instana Self-Hosted (Custom Edition)** on Red Hat OpenShift 4.18+ inside air-gapped sovereign environments.

---

## 🏗️ Architectural Overview

```mermaid
graph TD
    subgraph "Air-Gapped OpenShift Cluster"
        InstanaOp["Instana Agent Operator<br/>(Namespace: instana-agent)"]
        
        subgraph "Node Level"
            AgentDS["Instana Agent DaemonSet<br/>(1-Second Metric Resolution)"]
            Webhook["Instana AutoTrace Webhook<br/>(Mutating Admission Controller)"]
            AppPod["Java Microservice Pod<br/>(Bytecode Instrumented)"]
        end

        InstanaOp --> AgentDS
        InstanaOp --> Webhook
        Webhook -.->|Automated Java Instrumentation| AppPod
        AppPod -->|100% Unsampled AutoTrace Spans| AgentDS
        AppPod -->|AutoProfile Continuous Profiling| AgentDS
    end

    subgraph "On-Premises Infrastructure"
        InstanaBackend["Instana Self-Hosted Backend<br/>(ClickHouse / Kafka / Cassandra)"]
        KafkaCluster["Apache Kafka (Broker & Topics)"]
        SQLServer["Microsoft SQL Server (Port 1433)"]
        vSphere["VMware vCenter"]
    end

    AgentDS -->|Internal TLS Push| InstanaBackend
    AgentDS -->|Kafka Sensor (JMX & Offsets)| KafkaCluster
    AgentDS -->|SQL Server Sensor (DMV Metrics)| SQLServer
    AgentDS -->|vCenter Sensor (SOAP API)| vSphere
```

---

## 🔑 Key Architectural Capabilities

### 1. AutoTrace™ Zero-Touch Bytecode Instrumentation
Instana's **AutoTrace Webhook** provides automated tracing without code changes:
- Injects a lightweight native agent library upon container startup.
- Captures 100% of incoming and outgoing HTTP/REST requests, JDBC queries, and Kafka producer/consumer records.
- Automatically handles asynchronous context propagation across multi-threaded executor pools and reactive pipelines (Spring WebFlux, Reactor).

### 2. AutoProfile™ Always-On Continuous Profiling
- Operates continuously in production with less than 2% CPU overhead.
- Samples thread states (RUNNABLE, TIMED_WAITING, BLOCKED) and memory allocation events.
- Generates interactive **Flame Graphs** natively correlated with distributed transaction traces, allowing instant drill-down from a slow span into the exact Java method consuming execution time.

### 3. Dynamic Graph Topology Engine
Instana continuously builds a real-time graph model representing:
- Physical infrastructure (Hosts, VMs, NICs)
- Container orchestration (OpenShift Nodes, Pods, Namespaces)
- Runtimes (JVMs, CLRs)
- Application architecture (Services, Endpoints)
When an anomaly occurs, the Dynamic Graph traverses parent-child and peer relationships to isolate the root cause within seconds.

---

## 🚀 Deployment Instructions

### Step 1: Push Offline Images to Local Registry
```bash
skopeo copy docker://icr.io/instana/agent-operator:latest \
  docker://registry.internal.corp/instana/agent-operator:latest

skopeo copy docker://icr.io/instana/agent:latest \
  docker://registry.internal.corp/instana/agent:latest

skopeo copy docker://icr.io/instana/autotrace-webhook:latest \
  docker://registry.internal.corp/instana/autotrace-webhook:latest
```

### Step 2: Deploy Instana Agent Operator
```bash
oc create namespace instana-agent
oc apply -f instana-agent-daemonset.yaml
```

### Step 3: Validate Sensor Discovery
Verify that the Instana agent discovers the OpenShift cluster, Kafka brokers, and Java runtimes:
```bash
oc logs -n instana-agent -l app.kubernetes.io/name=instana-agent --tail=100
```
Look for:
`AutoTrace Webhook active`, `Discovered JVM process`, and `Kafka sensor connected`.
