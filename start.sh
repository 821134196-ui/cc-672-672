#!/usr/bin/env bash
# 一条命令启动前后端：构建 Svelte → 拷入 Quarkus 静态资源 → 打包 → 启动
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# 定位 JDK：优先仓库内 .tools（免安装），其次 JAVA_HOME / 系统 java
if [ -z "${JAVA_HOME:-}" ] && [ -d "$ROOT/.tools" ]; then
  JAVA_HOME="$(find "$ROOT/.tools" -maxdepth 1 -type d -name 'jdk-*' | head -1 || true)"
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v java >/dev/null 2>&1 || { echo "未找到 Java 17+，请先安装 JDK"; exit 1; }

# 定位 Maven：优先系统 mvn，其次仓库内 .tools
MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  MVN="$(find "$ROOT/.tools" -maxdepth 3 -name mvn -type f -path '*bin*' | head -1 || true)"
fi
[ -n "$MVN" ] || { echo "未找到 Maven"; exit 1; }

echo "==> [1/4] 构建前端 (Svelte)"
cd "$ROOT/frontend"
[ -d node_modules ] || npm install --no-audit --no-fund
npm run build

echo "==> [2/4] 部署前端到 Quarkus 静态资源"
rm -rf "$ROOT/backend/src/main/resources/META-INF/resources"
mkdir -p "$ROOT/backend/src/main/resources/META-INF/resources"
cp -r "$ROOT/frontend/dist/." "$ROOT/backend/src/main/resources/META-INF/resources/"

echo "==> [3/4] 打包后端 (Quarkus)"
cd "$ROOT/backend"
$MVN -q -B package -DskipTests

echo "==> [4/4] 启动  http://localhost:8080  (调度台 / 乘客 / 司机 三个页面)"
echo "    数据库: backend/data/bus.db (SQLite, 每次启动重建演示数据)"
mkdir -p "$ROOT/backend/data"
exec java -jar target/quarkus-app/quarkus-run.jar
