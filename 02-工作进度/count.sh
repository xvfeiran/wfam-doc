#!/bin/bash
file="测试文档.md"

count_section() {
    start=$1
    end=$2
    name=$3
    passed=$(sed -n "${start},${end}p" "$file" | grep -c "✅ 通过")
    pending=$(sed -n "${start},${end}p" "$file" | grep -c "⏳ 待测试")
    testing=$(sed -n "${start},${end}p" "$file" | grep -c "🔄 测试中")
    total=$((passed + pending + testing))
    if [ $total -gt 0 ]; then
        pct=$((passed * 100 / total))
    else
        pct=0
    fi
    printf "%s: total=%d passed=%d pending=%d testing=%d pct=%d%%\n" "$name" $total $passed $pending $testing $pct
}

count_section 83 245 "二、退货单管理测试"
count_section 246 346 "三、售后件管理测试"
count_section 347 417 "四、精分析报告测试"
count_section 418 450 "五、审批流程测试"
count_section 451 507 "六、统计报表测试"
count_section 508 572 "七、系统设置测试"
count_section 573 592 "八、基础服务测试"
count_section 676 9999 "十二、导入管理测试"
