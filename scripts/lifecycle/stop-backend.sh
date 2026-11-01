#!/usr/bin/env bash
# OAISS CHAIN - 后端停止脚本
# 停止监听 8080 端口的 Java 进程

set -euo pipefail

stopped=false

if command -v lsof >/dev/null 2>&1; then
  pids=$(lsof -ti tcp:8080 -sTCP:LISTEN 2>/dev/null || true)
  if [[ -n "${pids}" ]]; then
    for pid in $pids; do
      cmd=$(ps -p "$pid" -o comm= 2>/dev/null || true)
      if [[ "$cmd" == *java* ]]; then
        echo "[STOP] Stopping backend process $pid"
        kill "$pid" 2>/dev/null || true
        stopped=true
      fi
    done
  fi
fi

if [[ "$stopped" == false ]] && command -v netstat >/dev/null 2>&1; then
  pids=$(netstat -ano 2>/dev/null | grep ":8080" | grep "LISTEN" | awk '{print $NF}' | sort -u || true)
  if [[ -n "${pids}" ]]; then
    for pid in $pids; do
      [[ -z "$pid" || "$pid" == "0" ]] && continue
      echo "[STOP] Stopping backend process $pid"
      if command -v taskkill >/dev/null 2>&1; then
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
      else
        kill "$pid" 2>/dev/null || true
      fi
      stopped=true
    done
  fi
fi

# Fallback: Windows tasklist (for WSL where lsof/netstat can't see Windows processes)
if [[ "$stopped" == false ]] && command -v tasklist.exe >/dev/null 2>&1; then
  # Use wmic to find Java processes with oaiss-chain in command line
  pids=$(wmic.exe process where "name='java.exe' and commandline like '%oaiss-chain%'" get processid 2>/dev/null | grep -oE '[0-9]+' || true)
  if [[ -z "${pids}" ]]; then
    # Broader fallback: find Java processes on port 8080 via netstat from Windows
    pids=$(cmd.exe /c "netstat -ano | findstr :8080 | findstr LISTENING" 2>/dev/null | awk '{print $NF}' | sort -u || true)
  fi
  if [[ -n "${pids}" ]]; then
    for pid in $pids; do
      [[ -z "$pid" || "$pid" == "0" ]] && continue
      # Verify it's a Java process before killing
      pname=$(tasklist.exe //FI "PID eq $pid" //FO CSV 2>/dev/null | grep -i java || true)
      if [[ -n "$pname" ]]; then
        echo "[STOP] Stopping Java process $pid via taskkill"
        taskkill.exe //F //PID "$pid" >/dev/null 2>&1 || true
        stopped=true
      fi
    done
  fi
fi

if [[ "$stopped" == false ]]; then
  echo "[STOP] No backend process is listening on :8080"
  exit 0
fi

echo "[STOP] Backend stop command completed"
