#!/usr/bin/env bash
# ==============================================================================
# Script: inject-kafka-lag.sh
# Purpose: Simulates Kafka Consumer Group Lag to evaluate Observability Platforms
# Target: Pillar 1 (Metrics), Pillar 3 (Distributed Tracing), Pillar 7 (AIOps)
# ==============================================================================

set -euo pipefail

KAFKA_BOOTSTRAP="kafka-cluster-kafka-bootstrap.enterprise-messaging.svc:9092"
TOPIC_NAME="orders.events"
CONSUMER_GROUP="inventory-processing-group"
NAMESPACE="enterprise-workloads"
DEPLOYMENT_NAME="core-order-microservice"

echo "==================================================================="
echo " [CHAOS TEST] Initiating Apache Kafka Consumer Group Lag Injection"
echo "==================================================================="

echo "[1/4] Simulating Consumer Failure: Scaling down consumer deployment to 0 replicas..."
kubectl scale deployment "${DEPLOYMENT_NAME}" -n "${NAMESPACE}" --replicas=0

echo "[2/4] Flooding topic '${TOPIC_NAME}' with 5,000 synthetic order messages..."
for i in $(seq 1 5000); do
  echo "ORDER_ID_${i}:{\"orderId\":\"ORD-${i}\",\"amount\":$((RANDOM % 500 + 10)),\"timestamp\":\"$(date -u +'%Y-%m-%dT%H:%M:%SZ')\"}"
done | kubectl run kafka-producer-flood --rm -i --restart='Never' \
  --image=registry.internal.corp/enterprise/kafka-tools:latest -n "${NAMESPACE}" -- \
  bin/kafka-console-producer.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" --topic "${TOPIC_NAME}" --property "parse.key=true" --property "key.separator=:"

echo "[3/4] Inspecting Consumer Group Lag..."
kubectl run kafka-lag-checker --rm -i --restart='Never' \
  --image=registry.internal.corp/enterprise/kafka-tools:latest -n "${NAMESPACE}" -- \
  bin/kafka-consumer-groups.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" --describe --group "${CONSUMER_GROUP}"

echo ""
echo "[OBSERVABILITY VALIDATION CHECKPOINT]"
echo "Check your Observability Platform Console:"
echo " 1. Dynatrace: Verify Kafka Consumer Lag anomaly detection in Davis AI."
echo " 2. Instana: Verify Dynamic Graph surfaces 'Lag spike on ${CONSUMER_GROUP}'."
echo " 3. Elastic/Grafana: Verify kafka_consumer_lag metric alert firing."
echo ""
read -p "Press [ENTER] to recover the consumer and process the backlog..."

echo "[4/4] Scaling consumer back to 2 replicas to drain queue..."
kubectl scale deployment "${DEPLOYMENT_NAME}" -n "${NAMESPACE}" --replicas=2
echo "Consumer scaled up. Telemetry should reflect declining lag and burst CPU utilization."
