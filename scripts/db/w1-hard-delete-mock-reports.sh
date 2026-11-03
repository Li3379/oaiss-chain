#!/usr/bin/env bash
# ============================================================================
# W1.4：硬删除 11 份 tx_mock_ 报告
#
# 前置（必须已全部完成）：
#   1. w1-fix-frozen-quota.sh         —— frozen 修正为真实值
#   2. w1-cancel-orphan-auction-orders.sh —— 撤销旧路径孤儿挂单，frozen 归零
#   3. w1-revoke-quota-261-262.sh     —— 冲正 261/262 已发放配额
#
# 备份：.omc/state/w1-backup-reports.sql（含 11 行完整 INSERT，唯一回滚依据）
#
# 删除清单：
#   27,53,117,155,170,179,193,194,196   —— 未发放配额，直接删
#   261,262                              —— 已冲正（state=REVOKED），可删
#
# 用法：
#   bash scripts/db/w1-hard-delete-mock-reports.sh --dry-run
#   bash scripts/db/w1-hard-delete-mock-reports.sh --apply
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"
IDS="27,53,117,155,170,179,193,194,196,261,262"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W1.4 硬删除 mock 报告（mode=$MODE）"
echo "=========================================="

echo "待删清单核对："
run_sql "SELECT CONCAT('  id=',id,' state=',COALESCE(allocation_state,'NULL'),' amount=',COALESCE(quota_allocated_amount,'NULL')) FROM carbon_report WHERE id IN ($IDS)"

echo
echo "前置校验："
N=$(run_sql "SELECT COUNT(*) FROM carbon_report WHERE id IN ($IDS)" | tr -d '[:space:]')
NON_REVOKED=$(run_sql "SELECT COUNT(*) FROM carbon_report WHERE id IN ($IDS) AND allocation_state='ALLOCATED'" | tr -d '[:space:]')

# 按**金额**校验而非按流水条数：
# 发放流水是每报告一条（ALLOCATION），冲正流水是合并一条（ALLOCATION_REVOKE，记在 261 名下）。
# 用条数校验会把「262 没有独立冲正行」误判成未冲正——金额守恒才是真正的判据。
ALLOC_SUM=$(run_sql "SELECT COALESCE(SUM(amount),0) FROM quota_transaction WHERE related_report_id IN ($IDS) AND change_type='ALLOCATION'" | tr -d '[:space:]')
REVOKE_SUM=$(run_sql "SELECT COALESCE(SUM(amount),0) FROM quota_transaction WHERE change_type='ALLOCATION_REVOKE' AND remark LIKE 'W1 清理%'" | tr -d '[:space:]')

echo "  待删行数                = $N   （期望 11）"
echo "  仍处 ALLOCATED 的        = $NON_REVOKED   （期望 0）"
echo "  发放流水合计            = $ALLOC_SUM"
echo "  冲正流水合计            = $REVOKE_SUM   （应 >= 发放合计）"

if [[ "$N" -ne 11 || "$NON_REVOKED" -ne 0 ]]; then
    echo
    echo "✗ 前置校验未通过，中止。"
    exit 1
fi

python - "$ALLOC_SUM" "$REVOKE_SUM" <<'PY'
import sys
from decimal import Decimal
a, r = Decimal(sys.argv[1]), Decimal(sys.argv[2])
if r < a:
    print(f"  ✗ 冲正金额不足：发放 {a} vs 冲正 {r}")
    sys.exit(1)
print(f"  ✓ 前置校验通过（发放 {a} <= 冲正 {r}）")
PY

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行删除。加 --apply 才会真正删除。"
    exit 0
fi

echo
echo ">>> 执行硬删除..."
run_sql "DELETE FROM carbon_report WHERE id IN ($IDS);"

echo ">>> 删除后核对："
echo -n "  剩余 tx_mock_ 报告数 = "
run_sql "SELECT COUNT(*) FROM carbon_report WHERE is_deleted=0 AND blockchain_tx_hash LIKE 'tx_mock%'"
echo -n "  企业 1 报告总数       = "
run_sql "SELECT COUNT(*) FROM carbon_report WHERE is_deleted=0 AND enterprise_id=1"