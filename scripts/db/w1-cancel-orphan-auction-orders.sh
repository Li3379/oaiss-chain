#!/usr/bin/env bash
# ============================================================================
# W1 前置：按 TradeService.cancelTrade 的语义，撤销 3 笔旧路径孤儿挂单
#
# 背景：
#   这 3 笔挂单（transaction 194/199/204，trade_type=1 status=0，各 1 吨）
#   由**已删除的** POST /trade/auction 旧路径创建。W2 删掉该路径后，
#   它们既不在 auction_order 表（该表 0 行），也无法走 DoubleAuctionService.cancelOrder，
#   成为「无任何业务入口可撤销」的幽灵挂单。
#
# 处置依据：TradeService.cancelTrade（L221-263）的处理逻辑，逐条对应：
#   1. 校验可撤销：TradeStatusEnum.isCancellable() → PENDING(0) 可撤销 ✓
#   2. status = CANCELLED(3)
#   3. 解冻：frozen -= quantity；tradable += quantity
#
# 用法：
#   bash scripts/db/w1-cancel-orphan-auction-orders.sh --dry-run
#   bash scripts/db/w1-cancel-orphan-auction-orders.sh --apply
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W1 撤销旧路径孤儿挂单（mode=$MODE）"
echo "=========================================="

echo "目标记录（trade_type=1 且 status=0 且 seller_id=2）："
run_sql "SELECT CONCAT('  id=',id,' no=',trade_no,' qty=',quantity,' status=',status) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2"

TOTAL=$(run_sql "SELECT COALESCE(SUM(quantity),0) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2" | tr -d '[:space:]')
BEFORE=$(run_sql "SELECT CONCAT('tradable=',carbon_tradable,' frozen=',carbon_frozen) FROM enterprise WHERE id=1")

echo
echo "撤销总量：$TOTAL 吨"
echo "企业 1 当前：$BEFORE"

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行写入。加 --apply 才会真正修改。"
    exit 0
fi

echo
echo ">>> 执行撤单（对应 cancelTrade 的三步）..."
run_sql "
SET @qty = (SELECT COALESCE(SUM(quantity),0) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2);
-- 1/2. 状态置 CANCELLED(3)
UPDATE transaction SET status = 3, updated_at = NOW()
 WHERE trade_type=1 AND status=0 AND seller_id=2;
-- 3. 解冻并回补可交易
UPDATE enterprise SET carbon_tradable = carbon_tradable + @qty WHERE id = 1;
UPDATE enterprise SET carbon_frozen = GREATEST(carbon_frozen - @qty, 0) WHERE id = 1;
"

echo ">>> 撤销后："
run_sql "SELECT CONCAT('tradable=',carbon_tradable,' frozen=',carbon_frozen,' quota=',carbon_quota) FROM enterprise WHERE id=1"
run_sql "SELECT CONCAT('剩余 status=0 挂单数=', COUNT(*)) FROM transaction WHERE trade_type=1 AND status=0 AND seller_id=2"