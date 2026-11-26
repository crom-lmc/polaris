#!/usr/bin/env bash
# Polaris 生产滚动重启：一次只重启 1 个服务，确认 healthy/running 后再继续下一个。
#
# 前提：新 jar 已上传到对应容器挂载路径。本脚本只做重启，不执行 docker compose up -d。
# 用法：
#   ./rolling-restart.sh                    # 按 compose 当前服务列表逐个滚动重启
#   ./rolling-restart.sh finance contract   # 只滚动重启指定服务
#   DRY_RUN=1 ./rolling-restart.sh          # 只打印计划，不执行
# 可调：INTERVAL_SECONDS（默认 30）、WAIT_SECONDS（默认 240）、COMPOSE_FILE。

set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-/opt/polaris/docker-compose.yml}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-30}"
WAIT_SECONDS="${WAIT_SECONDS:-240}"
DRY_RUN="${DRY_RUN:-0}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

usage() {
  echo "Usage: $0 [service ...]"
  echo "Env: COMPOSE_FILE=/opt/polaris/docker-compose.yml INTERVAL_SECONDS=30 WAIT_SECONDS=240 DRY_RUN=0"
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
esac

if [ ! -f "$COMPOSE_FILE" ]; then
  log "ERROR: compose file not found: $COMPOSE_FILE"
  exit 1
fi

if [ "$#" -gt 0 ]; then
  services=("$@")
else
  mapfile -t services < <(docker compose -f "$COMPOSE_FILE" ps --services)
fi

if [ "${#services[@]}" -eq 0 ]; then
  log "ERROR: no services selected"
  exit 1
fi

log "ROLLING START: ${#services[@]} services, interval=${INTERVAL_SECONDS}s, wait=${WAIT_SECONDS}s, dry_run=${DRY_RUN}"
if [ "$DRY_RUN" = "1" ]; then
  log "PLAN: ${services[*]}"
  exit 0
fi

total="${#services[@]}"
for ((idx = 0; idx < total; idx++)); do
  svc="${services[$idx]}"
  cid="$(docker compose -f "$COMPOSE_FILE" ps -q "$svc" 2>/dev/null || true)"
  if [ -z "$cid" ]; then
    log "SKIP $svc: no running container"
    continue
  fi

  log "RESTART [$((idx + 1))/$total] $svc ($cid)"
  docker compose -f "$COMPOSE_FILE" restart "$svc"

  deadline=$((SECONDS + WAIT_SECONDS))
  status="unknown"
  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)"
    case "$status" in
      healthy|running)
        log "OK $svc status=$status"
        break
        ;;
      *)
        log "WAIT $svc status=$status"
        ;;
    esac
    sleep 5
  done

  if [ "$status" != "healthy" ] && [ "$status" != "running" ]; then
    log "FAIL $svc status=$status after ${WAIT_SECONDS}s; stop rolling"
    exit 1
  fi

  if [ "$idx" -lt "$((total - 1))" ]; then
    log "SLEEP ${INTERVAL_SECONDS}s before next service"
    sleep "$INTERVAL_SECONDS"
  fi
done

log "ROLLING DONE"
