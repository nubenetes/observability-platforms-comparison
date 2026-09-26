#!/usr/bin/env bash
# ==============================================================================
# Script: inject-thread-contention.sh
# Purpose: Simulates Thread Contention & CPU Backtracking in Java Workloads
# Target: Pillar 1 (JVM Metrics), Pillar 4 (Continuous Profiling Flame Graphs)
# ==============================================================================

set -euo pipefail

TARGET_URL="${1:-http://core-order-microservice.enterprise-workloads.svc:8080}"
CONCURRENCY=15
DURATION_SEC=60

echo "==================================================================="
echo " [CHAOS TEST] Initiating CPU Saturation & Java Thread Lock Contention"
echo " Target Endpoint: ${TARGET_URL}"
echo " Concurrency: ${CONCURRENCY} workers | Duration: ${DURATION_SEC}s"
echo "==================================================================="

echo "[1/2] Injecting CPU Saturation (Regex Backtracking) & Synchronized Monitor Contention..."

end_time=$((SECONDS + DURATION_SEC))

while [ $SECONDS -lt $end_time ]; do
  for i in $(seq 1 $CONCURRENCY); do
    # Alternate between CPU regex backtracking and Java synchronized block contention
    if [ $((i % 2)) -eq 0 ]; then
      curl -s "${TARGET_URL}/api/faults/cpu-lock?durationMs=1500" > /dev/null &
    else
      curl -s "${TARGET_URL}/api/faults/thread-contention?threadCount=8" > /dev/null &
    fi
  done
  sleep 1
done

wait
echo ""
echo "==================================================================="
echo " [CHAOS TEST COMPLETE] Telemetry Verification Checkpoint"
echo "==================================================================="
echo "1. Verify Continuous Profiling (Flame Graphs):"
echo "   - Dynatrace / Instana / Pyroscope / Elastic Universal Profiling"
echo "   - Call tree must show: 'DiagnosticFaultController.triggerCpuSaturation'"
echo "   - Thread states must show 'BLOCKED' on synchronized monitor 'lockA'"
echo "2. Verify JVM Metrics:"
echo "   - jvm_threads_blocked count spikes"
echo "   - process_cpu_usage approaches container CFS quota limit"
echo "==================================================================="
