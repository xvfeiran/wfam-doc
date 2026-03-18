#!/bin/bash
file="测试文档.md"

echo "二、退货单管理测试 (lines 83-553)"
sed -n '83,553p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "三、售后件管理测试 (lines 555-657)"
sed -n '555,657p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "四、精分析报告测试 (lines 659-750)"
sed -n '659,750p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "五、审批流程测试 (lines 752-800)"
sed -n '752,800p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "六、统计报表测试 (lines 802-880)"
sed -n '802,880p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "七、系统设置测试 (lines 882-1000)"
sed -n '882,1000p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "八、基础服务测试 (lines 1002-1030)"
sed -n '1002,1030p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'

echo "十二、导入管理测试 (lines 1057-end)"
sed -n '1057,$p' "$file" | grep "✅ 通过\|⏳ 待测试\|🔄 测试中\|待测试" | awk '/通过/ {p++} /待测试/ {t++} /测试中/ {i++} END {total=p+t+i; pct=int(p/total*100); printf "total=%d passed=%d pending=%d inprog=%d pct=%d%%\n", total, p, t, i, pct}'
