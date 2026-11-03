#!/usr/bin/env bash
# ============================================================================
# W1.2：冲正报告 261 / 262 的配额（各 985.8024 吨）
#
# 处置依据（逐行对应 QuotaAllocationService.revokeForReport L197-251
# 与 QuotaLedgerService.revokeAllocation L289-325）：
#   1. 校验：allocation_state 已发放、amount > 0、used 未超过发放时快照、frozen == 0
#   2. 账本：carbon_quota -= amount；carbon_tradable -= amount；carbon_used 不动
#   3. 写 ALLOCATION_REVOKE 流水（allocation_key 留 NULL）
#   4. 报告：allocation_state = REVOKED，quota_allocated_amount / quota_used_snapshot 置 NULL
#
# 前置：frozen 必须为 0（已由 w1-fix-frozen-quota.sh + w1-cancel-orphan-auction-orders.sh 完成）
#
# 用法：
#   bash scripts/db/w1-revoke-quota-261-262.sh --dry-run
#   bash scripts/db/w1-revoke-quota-261-262.sh --apply
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"
REPORTS="261,262"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W1.2 冲正配额（报告 $REPORTS，mode=$MODE）"
echo "=========================================="

echo "目标报告："
run_sql "SELECT CONCAT('  id=',id,' state=',COALESCE(allocation_state,'NULL'),' amount=',COALESCE(quota_allocated_amount,'NULL'),' used_snap=',COALESCE(quota_used_snapshot,'NULL'),' tx=',COALESCE(blockchain_tx_hash,'NULL')) FROM carbon_report WHERE id IN ($REPORTS)"

echo
echo "企业 1 当前余额："
run_sql "SELECT CONCAT('  quota=',carbon_quota,' tradable=',carbon_tradable,' used=',carbon_used,' frozen=',carbon_frozen) FROM enterprise WHERE id=1"

# ---- 前置校验（与 revokeForReport 一致，不满足即中止）----
FROZEN=$(run_sql "SELECT carbon_frozen FROM enterprise WHERE id=1" | tr -d '[:space:]')
TOTAL=$(run_sql "SELECT COALESCE(SUM(quota_allocated_amount),0) FROM carbon_report WHERE id IN ($REPORTS)" | tr -d '[:space:]')

echo
echo "待冲正合计：$TOTAL 吨"

python - "$FROZEN" "$TOTAL" <<'PY'
import sys
from decimal import Decimal
frozen, total = Decimal(sys.argv[1]), Decimal(sys.argv[2])
if frozen != 0:
    print(f"  ✗ 前置校验失败：carbon_frozen={frozen}，冲正要求在途冻结为 0")
    sys.exit(1)
if total <= 0:
    print(f"  ✗ 前置校验失败：待冲正金额={total}")
    sys.exit(1)
print(f"  ✓ 前置校验通过（frozen=0，amount={total} > 0）")
PY

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行写入。加 --apply 才会真正修改。"
    exit 0
fi

echo
echo ">>> 执行冲正..."
run_sql "
SET @total = (SELECT COALESCE(SUM(quota_allocated_amount),0) FROM carbon_report WHERE id IN ($REPORTS));
SET @tradable_before = (SELECT carbon_tradable FROM enterprise WHERE id=1);
SET @frozen_before   = (SELECT carbon_frozen   FROM enterprise WHERE id=1);
SET @used_before     = (SELECT carbon_used     FROM enterprise WHERE id=1);

-- 账本：quota 与 tradable 同减，used 不动
UPDATE enterprise
   SET carbon_quota    = carbon_quota    - @total,
       carbon_tradable = carbon_tradable - @total
 WHERE id = 1;

-- 流水（对应 QuotaLedgerService.writeLedgerWithReport，allocation_key 留 NULL）
INSERT INTO quota_transaction
    (created_at, updated_at, is_deleted, enterprise_id, tx_no, change_type, amount,
     tradable_before, tradable_after, frozen_before, frozen_after,
     used_before, used_after, source, related_report_id, operator_id, remark,
     allocation_key, allocation_seq)
VALUES
    (NOW(), NOW(), 0, 1, CONCAT('QRV-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), '-W1'),
     'ALLOCATION_REVOKE', @total,
     @tradable_before, @tradable_before - @total, @frozen_before, @frozen_before,
     @used_before, @used_before, 'AUDIT', 261, 1, 'W1 清理：mock 哈希报告冲正（261/262 合并）',
     NULL, NULL);

-- 报告状态机：REVOKED，清空金额与快照
UPDATE carbon_report
   SET allocation_state = 'REVOKED',
       quota_allocated_amount = NULL,
       quota_used_snapshot = NULL,
       updated_at = NOW()
 WHERE id IN ($REPORTS);
"

echo ">>> 冲正后："
run_sql "SELECT CONCAT('  quota=',carbon_quota,' tradable=',carbon_tradable,' used=',carbon_used,' frozen=',carbon_frozen) FROM enterprise WHERE id=1"
run_sql "SELECT CONCAT('  id=',id,' state=',COALESCE(allocation_state,'NULL'),' amount=',COALESCE(quota_allocated_amount,'NULL')) FROM carbon_report WHERE id IN ($REPORTS)"