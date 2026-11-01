#!/usr/bin/env bash
# bootstrap-remote-release-host.sh 的冒烟测试。
#
# 背景：该脚本用 `chown -R "$DEPLOY_USER":"$DEPLOY_USER"` 设置归属，
# 隐含假设「用户名与组名相同」。这个假设在多数 Linux 发行版上成立，
# 在 GitHub Runner（用户 runner，无同名组）和 Windows 上不成立——
# 脚本会直接退出，而 CI 只看到 exit 1，看不出是 chown 的问题。
#
# 本测试锁定三点：
#   1. 目录结构被完整创建
#   2. 用户名与组名不同时仍能成功（组不存在则退回主组）
#   3. 输出包含 CI 断言依赖的关键行
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BOOTSTRAP="$REPO_ROOT/scripts/bootstrap-remote-release-host.sh"

PASS=0
FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; }

TMP_DIR="$(mktemp -d 2>/dev/null || echo "$REPO_ROOT/tmp/bootstrap-test-$$")"
mkdir -p "$TMP_DIR"
TARGET="$TMP_DIR/release-host"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

echo "=== bootstrap-remote-release-host.sh 冒烟测试 ==="

# --- 用例 1：正常执行（不带 --deploy-user）---
echo "[1/3] 不带 --deploy-user"
if bash "$BOOTSTRAP" --target-dir "$TARGET" > "$TMP_DIR/out1.txt" 2>&1; then
    ok "退出码为 0"
else
    bad "退出码非 0"
    sed 's/^/      /' "$TMP_DIR/out1.txt" | tail -5
fi

for d in runtime-logs/backend runtime-logs/frontend runtime-logs/ml-service secrets/fabric backups scripts; do
    if [ -d "$TARGET/$d" ]; then ok "目录存在: $d"; else bad "目录缺失: $d"; fi
done

if grep -q "Directory layout ready" "$TMP_DIR/out1.txt"; then
    ok "输出含 'Directory layout ready'"
else
    bad "缺少 'Directory layout ready'"
fi

# --- 用例 2：用户名与组名不同（核心回归）---
echo "[2/3] --deploy-user 指向一个组名不存在的用户"
CURRENT_USER="$(id -un)"
TARGET2="$TMP_DIR/release-host-2"
if bash "$BOOTSTRAP" --target-dir "$TARGET2" --deploy-user "$CURRENT_USER" > "$TMP_DIR/out2.txt" 2>&1; then
    ok "用户名/组名不一致时仍成功"
else
    bad "用户名/组名不一致时失败"
    sed 's/^/      /' "$TMP_DIR/out2.txt" | tail -5
fi

# --- 用例 3：不存在的用户名应给出清晰错误，而非静默成功 ---
echo "[3/3] --deploy-user 指向不存在的用户"
TARGET3="$TMP_DIR/release-host-3"
OUT3="$TMP_DIR/out3.txt"
bash "$BOOTSTRAP" --target-dir "$TARGET3" --deploy-user "no-such-user-$$" > "$OUT3" 2>&1
if grep -qiE "no-such-user|unknown user|不存在|invalid user" "$OUT3"; then
    ok "不存在的用户有明确报错"
else
    bad "不存在的用户未给出明确报错"
    sed 's/^/      /' "$OUT3" | tail -5
fi

echo ""
echo "通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" -eq 0 ] || exit 1
