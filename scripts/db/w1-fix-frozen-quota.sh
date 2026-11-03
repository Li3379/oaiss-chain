#!/usr/bin/env bash
# ============================================================================
# W1 前置：修正企业 1 的冻结配额账实不符
#
# 依据（2026-11-02 实测取证）：
#   carbon_frozen = 155.0000
#   可归因：
#     3 吨  —— 3 笔 status=0 的真实拍卖挂单（transaction 194/199/204，各 1 吨）
#     30 吨 —— 交易 215（P2P，status=3 已取消，completed_at=NULL）取消时未解冻
#   不可归因：
#     122 吨 —— transaction 表中无任何对应记录（纯账实漂移）
#
# 处置：把 frozen 从 155 修正为 3（仅保留真实挂单的冻结），
#       差额 152 吨回到 tradable 之前，先确认 carbon_quota 总额是否需要同步。
#
# 用法：
#   bash scripts/db/w1-fix-frozen-quota.sh --dry-run   # 只打印差异
#   bash scripts/db/w1-fix-frozen-quota.sh --apply     # 实际执行
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W1 冻结配额修正（mode=$MODE）"
echo "=========================================="

CURRENT=$(run_sql "SELECT carbon_frozen FROM enterprise WHERE id=1" | tr -d '[:space:]')
QUOTA=$(run_sql "SELECT carbon_quota FROM enterprise WHERE id=1" | tr -d '[:space:]')
TRADABLE=$(run_sql "SELECT carbon_tradable FROM enterprise WHERE id=1" | tr -d '[:space:]')

# 可归因的冻结：status=0 的拍卖挂单
PENDING=$(run_sql "SELECT COALESCE(SUM(quantity),0) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2" | tr -d '[:space:]')

echo "当前值："
echo "  carbon_quota    = $QUOTA"
echo "  carbon_tradable = $TRADABLE"
echo "  carbon_frozen   = $CURRENT"
echo "  真实挂单冻结     = $PENDING"
echo

python - "$CURRENT" "$TRADABLE" "$PENDING" <<'PY'
import sys
from decimal import Decimal
cur, trad, pend = (Decimal(x) for x in sys.argv[1:4])
delta = cur - pend
print("拟修正：")
print(f"  frozen   : {cur} -> {pend}        (减少 {delta})")
print(f"  tradable : {trad} -> {trad + delta}  (增加 {delta}，冻结释放回可交易)")
print()
print("说明：carbon_quota 总额不变——冻结与可交易是同一批配额的两个状态位，")
print("      释放只是把它们从 frozen 移到 tradable，不改变企业持有的总量。")
print(f"      修正后 frozen+tradable = {pend + trad + delta}，应与修正前 {cur + trad} 相等。")
assert pend + trad + delta == cur + trad, "内部校验失败：总量不守恒"
print("      ✓ 总量守恒校验通过")
PY

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行任何写入。加 --apply 才会真正修改。"
    exit 0
fi

echo
echo ">>> 执行修正..."
# 注意：MySQL 单条 UPDATE 里，右侧表达式读的是**更新前**的列值，
# 但多处引用同一列时求值顺序容易踩坑。这里用变量显式固定增量：
#   1. 先算出真实挂单冻结量 @pend 与待释放差额 @delta
#   2. 先改 tradable（用 @delta），再改 frozen（用 @pend）
# 顺序不可颠倒，且都不依赖同语句内被改列的新值。
run_sql "
SET @pend = (SELECT COALESCE(SUM(quantity),0) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2);
SET @delta = (SELECT carbon_frozen FROM enterprise WHERE id=1) - @pend;
UPDATE enterprise SET carbon_tradable = carbon_tradable + @delta WHERE id=1;
UPDATE enterprise SET carbon_frozen = @pend WHERE id=1;
"
echo ">>> 修正后："
run_sql "SELECT CONCAT('quota=',carbon_quota,' tradable=',carbon_tradable,' frozen=',carbon_frozen) FROM enterprise WHERE id=1"