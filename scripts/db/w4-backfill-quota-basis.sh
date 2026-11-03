#!/usr/bin/env bash
# ============================================================================
# W4：配额依据字段回填（只补可推导的周期字段）
#
# 实测结论（2026-11-02）：
#   139 份报告中 135 份的 period_year / period_quarter / output_quantity / unit_type 全为 NULL，
#   且 emission_data 里**不存在任何产量字段**（outputQuantity / 发电量 命中 0 份，
#   样本只有 {"scope1":[{"name":"gas","activity_data":...}]}）。
#
# 因此：
#   - periodYear / periodQuarter  → 可从 accounting_period 推导（严格白名单）
#   - outputQuantity / unitType   → **数据源中不存在，无法回填**
#                                  标为「不可发放」，不填 0（0 会被当成真实产量）
#
# 周期格式实测分布（139 份）：
#   YYYY-QN          113  ✅
#   YYYY-QN-REG       17  ✅
#   YYYY-MM-DD        14  ⚠️ 仅年份可推，季度不可推
#   YY-QN（21-Q1/26-Q1） 2  ❌ 两位年份，标记不可推导
#   其他               2  ❌ 标记不可推导
#
# ⚠️ 不复用 CarbonService.extractYear：它对无法解析的输入**静默退回当前年度**，
#    回填场景下这会写错 139 份报告的年份且不报警。回填必须显式区分「推导成功/失败」。
#
# 用法：
#   bash scripts/db/w4-backfill-quota-basis.sh --dry-run
#   bash scripts/db/w4-backfill-quota-basis.sh --apply
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W4 配额依据回填（mode=$MODE）"
echo "=========================================="

echo "周期格式分布："
run_sql "
SELECT CONCAT('  ', fmt, '  count=', cnt) FROM (
  SELECT CASE
    WHEN accounting_period REGEXP '^[0-9]{4}-Q[1-4]\$' THEN 'YYYY-QN'
    WHEN accounting_period REGEXP '^[0-9]{4}-Q[1-4]-REG\$' THEN 'YYYY-QN-REG'
    WHEN accounting_period REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$' THEN 'YYYY-MM-DD'
    WHEN accounting_period REGEXP '^[0-9]{2}-Q[1-4]\$' THEN 'YY-QN(两位年份)'
    ELSE '其他' END AS fmt,
    COUNT(*) AS cnt
  FROM carbon_report WHERE is_deleted = 0
  GROUP BY fmt
) t ORDER BY cnt DESC"

echo
echo "字段缺失情况："
run_sql "
SELECT CONCAT('  period_year 为空: ', SUM(period_year IS NULL),
              ' | output_quantity 为空: ', SUM(output_quantity IS NULL),
              ' | 总计: ', COUNT(*))
FROM carbon_report WHERE is_deleted = 0"

echo
echo "emission_data 是否含产量字段（应为 0）："
run_sql "SELECT CONCAT('  命中 ', COUNT(*), ' 份')
FROM carbon_report WHERE is_deleted = 0
  AND (emission_data LIKE '%outputQuantity%' OR emission_data LIKE '%发电量%')"

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行写入。加 --apply 才会真正回填。"
    echo
    echo "将执行的规则："
    echo "  YYYY-QN      → period_year=YYYY, period_quarter=N"
    echo "  YYYY-QN-REG  → 同上（REG 后缀不影响季度）"
    echo "  YYYY-MM-DD   → period_year=YYYY, period_quarter=NULL（季度不可推，不猜）"
    echo "  YY-QN / 其他 → 两字段均保持 NULL，标记不可推导"
    echo "  output_quantity / unit_type → 一律不动（数据源不存在）"
    exit 0
fi

echo
echo ">>> 执行回填（仅周期字段，跳过同企业同周期重复组）..."

# 关键约束：uk_report_period = (enterprise_id, period_year, period_quarter, is_deleted)
#
# 回填前这些报告的 period_year/quarter 全为 NULL，而 MySQL 唯一索引不约束 NULL，
# 所以同企业同周期可以存在多份（实测 2025-Q1 就有 18 份）。
# 一旦回填真实值就会撞唯一键，整条 UPDATE 回滚。
#
# 处置：只回填「同企业同周期仅 1 份」的报告；重复组整组跳过并列出清单，
# 交由人工判断「这些报告是否真的同周期」（可能是历史重复填报，也可能周期字段本身录错）。
# 不猜测、不去重、不填 0。

# 1) YYYY-QN / YYYY-QN-REG：唯一周期组才回填
run_sql "
UPDATE carbon_report r
   JOIN (
     SELECT enterprise_id, LEFT(accounting_period, 7) AS per
       FROM carbon_report
      WHERE is_deleted = 0 AND period_year IS NULL
        AND (accounting_period REGEXP '^[0-9]{4}-Q[1-4]\$'
          OR accounting_period REGEXP '^[0-9]{4}-Q[1-4]-REG\$')
      GROUP BY enterprise_id, LEFT(accounting_period, 7)
     HAVING COUNT(*) = 1
   ) uniq
     ON uniq.enterprise_id = r.enterprise_id
    AND uniq.per = LEFT(r.accounting_period, 7)
   SET r.period_year    = CAST(SUBSTRING(r.accounting_period, 1, 4) AS UNSIGNED),
       r.period_quarter = CAST(SUBSTRING(r.accounting_period, 7, 1) AS UNSIGNED),
       r.updated_at     = NOW()
 WHERE r.is_deleted = 0
   AND r.period_year IS NULL
   AND (r.accounting_period REGEXP '^[0-9]{4}-Q[1-4]\$'
     OR r.accounting_period REGEXP '^[0-9]{4}-Q[1-4]-REG\$');
"

# 2) YYYY-MM-DD：只推年份，季度留 NULL（季度不可推，不猜）
run_sql "
UPDATE carbon_report
   SET period_year = CAST(SUBSTRING(accounting_period, 1, 4) AS UNSIGNED),
       updated_at  = NOW()
 WHERE is_deleted = 0
   AND period_year IS NULL
   AND accounting_period REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$';
"

echo
echo ">>> 回填结果："
run_sql "
SELECT CONCAT('  已回填(有年份): ', SUM(period_year IS NOT NULL),
              ' | 未回填(仍为NULL): ', SUM(period_year IS NULL),
              ' | 有产量依据: ', SUM(output_quantity IS NOT NULL),
              ' | 总计: ', COUNT(*))
FROM carbon_report WHERE is_deleted = 0"

echo
echo ">>> 未回填明细（重复周期组 / 异常格式，需人工处置，不填 0）："
run_sql "
SELECT CONCAT('  id=', id, ' period=', accounting_period, ' report=', report_no)
FROM carbon_report
WHERE is_deleted = 0 AND period_year IS NULL
ORDER BY accounting_period, id"