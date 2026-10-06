#!/usr/bin/env bash
#
# 一键启动「客运大巴临时换车座位重排系统」
#   后端：Quarkus (Java 17) http://localhost:8080
#   前端：Svelte + Vite    http://localhost:5173  （/api 代理到 8080）
#
# 用法：
#   ./start.sh          启动前后端（Ctrl-C 一并停止）
#   ./start.sh test     仅运行后端自动化测试（7 个用例，含四个关键场景）
#   ./start.sh reset    重置演示数据后启动
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

# ---- Java / Maven 环境探测 ----
if [ -f "$HOME/tools/env.sh" ]; then
  # 本机用户空间安装的 JDK/Maven（无 root 权限环境）
  # shellcheck disable=SC1090
  source "$HOME/tools/env.sh"
fi

if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  echo "✗ 未找到 java / mvn。请安装 JDK 17+ 与 Maven 3.8+，或将其加入 PATH。" >&2
  exit 1
fi
echo "✓ $(java -version 2>&1 | head -1)"
echo "✓ $(mvn -version 2>&1 | head -1)"

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  echo "✗ 未找到 node / npm。请安装 Node.js 18+。" >&2
  exit 1
fi
echo "✓ node $(node -v)"

# ---- 子命令：test ----
if [ "${1:-}" = "test" ]; then
  cd "$BACKEND"
  exec mvn test
fi

# ---- 前端依赖（首次启动自动安装） ----
if [ ! -d "$FRONTEND/node_modules" ]; then
  echo "→ 首次启动，安装前端依赖…"
  (cd "$FRONTEND" && npm install)
fi

# ---- 可选：重置演示数据 ----
if [ "${1:-}" = "reset" ]; then
  echo "→ 清空后端数据库文件（data/bus.db）…"
  rm -f "$BACKEND/data/bus.db"
fi

mkdir -p "$BACKEND/data"

echo "→ 启动后端 http://localhost:8080 …"
( cd "$BACKEND" && mvn -q quarkus:dev -Ddebug=0 -Dquarkus.http.host=0.0.0.0 ) &
BACKEND_PID=$!

echo "→ 启动前端 http://localhost:5173 …"
( cd "$FRONTEND" && npm run dev ) &
FRONTEND_PID=$!

# Ctrl-C / 脚本退出时一并停止两个子进程
cleanup() {
  echo ""
  echo "→ 停止前后端…"
  kill "$FRONTEND_PID" "$BACKEND_PID" 2>/dev/null || true
  # 释放 8080 上可能残留的 quarkus 子进程
  pkill -P "$BACKEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo ""
echo "============================================================"
echo "  前端（打开此地址）:  http://localhost:5173"
echo "  后端 API:            http://localhost:8080/api/manifest"
echo "  日志:                /tmp/backend.log（控制台）与前端终端"
echo "  Ctrl-C 停止全部服务"
echo "============================================================"
echo ""

wait
