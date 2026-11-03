#!/usr/bin/env bash
# ============================================================================
# W1.5：重算企业 1 的年度评级（11 份 mock 报告已硬删，评级依据需同步）
#
# 算法严格对应 EmissionRatingService.recalculateYearlyRating（L74-96）
# 与 rateEnterprise / calculateLevel / calculateScore：
#   - 权威状态：APPROVED(3) + ON_CHAIN(5)
#   - 年度归属：accounting_period LIKE '<year>%'
#   - totalEmission = 该年度权威报告排放量之和
#   - level：<1000→A，<5000→B，<20000→C，<50000→D，其余 E
#   - score：分段线性（见下）
#   - source_report_count = 参与汇总的报告份数
#
# 用法：
#   bash scripts/db/w1-recalc-ratings.sh --dry-run
#   bash scripts/db/w1-recalc-ratings.sh --apply
# ============================================================================
set -euo pipefail

MODE="${1:---dry-run}"
CONTAINER="${MYSQL_CONTAINER:-oaiss-mysql}"

run_sql() {
    docker exec "$CONTAINER" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" oaiss_chain -N -e \"$1\""
}

echo "=========================================="
echo " W1.5 重算企业 1 年度评级（mode=$MODE）"
echo "=========================================="

echo "现存权威报告按年分布："
# 用子查询先算出年份列，外层再 GROUP BY 该列——避免 only_full_group_by 对
# SELECT 里 LEFT(...) 与 GROUP BY 表达式同一性判定失败。
run_sql "SELECT CONCAT('  ',y,'  reports=',c,'  total=',t) FROM (SELECT LEFT(accounting_period,4) AS y, COUNT(*) AS c, COALESCE(SUM(total_emission),0) AS t FROM carbon_report WHERE is_deleted=0 AND enterprise_id=1 AND status IN (3,5) GROUP BY LEFT(accounting_period,4)) AS g ORDER BY g.y"

echo
echo "当前评级表："
run_sql "SELECT CONCAT('  ',rating_year,' level=',rating_level,' total=',total_emission,' count=',COALESCE(source_report_count,'NULL')) FROM emission_rating WHERE enterprise_id=1 ORDER BY rating_year"

if [[ "$MODE" != "--apply" ]]; then
    echo
    echo "[dry-run] 未执行写入。加 --apply 才会真正修改。"
    exit 0
fi

echo
echo ">>> 执行重算..."
for YEAR in 2024 2025 2026 2027; do
    SUM=$(run_sql "SELECT COALESCE(SUM(total_emission),0) FROM carbon_report WHERE is_deleted=0 AND enterprise_id=1 AND status IN (3,5) AND accounting_period LIKE '$YEAR%'" | tr -d '[:space:]')
    CNT=$(run_sql "SELECT COUNT(*) FROM carbon_report WHERE is_deleted=0 AND enterprise_id=1 AND status IN (3,5) AND accounting_period LIKE '$YEAR%'" | tr -d '[:space:]')

    if [[ "$CNT" -eq 0 ]]; then
        echo "  $YEAR: 无权威报告，跳过"
        continue
    fi

    read -r LEVEL SCORE <<< "$(python - "$SUM" <<'PY'
import sys
from decimal import Decimal
e = Decimal(sys.argv[1])
if e < 1000:   lvl="A"; score=90+int((1-float(e)/1000)*10)
elif e < 5000: lvl="B"; score=70+int((1-float(e)/5000)*20)
elif e < 20000:lvl="C"; score=50+int((1-float(e)/20000)*20)
elif e < 50000:lvl="D"; score=30+int((1-float(e)/50000)*20)
else:          lvl="E"; score=max(10,int(30-(float(e)/50000)*20))
print(lvl, score)
PY
)"

    # emission_rating 表没有 (enterprise_id, rating_year) 唯一键，
    # ON DUPLICATE KEY 不会触发。因此先删该企业该年的旧行再插入，
    # 否则会累积重复评级行（实测重跑一次多出 4 行）。
    run_sql "DELETE FROM emission_rating WHERE enterprise_id=1 AND rating_year='$YEAR';"
    run_sql "
    INSERT INTO emission_rating
        (created_at, updated_at, is_deleted, enterprise_id, rating_year, total_emission,
         rating_level, rating_score, rated_by, source_report_count)
    VALUES (NOW(), NOW(), 0, 1, '$YEAR', $SUM, '$LEVEL', $SCORE, 1, $CNT);
    "
    echo "  $YEAR: total=$SUM count=$CNT -> level=$LEVEL score=$SCORE"
done

echo
echo ">>> 重算后："
run_sql "SELECT CONCAT('  ',rating_year,' level=',rating_level,' score=',rating_score,' total=',total_emission,' count=',COALESCE(source_report_count,'NULL')) FROM emission_rating WHERE enterprise_id=1 ORDER BY rating_year"