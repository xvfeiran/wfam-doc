import re

with open('测试文档.md', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    m = re.match(r'^\|\s*(\d+)\s*\|', line)
    if not m:
        continue
    # skip status def
    if 'draft' in line and 'Draft' in line:
        continue
    # find rows without recognized status
    if not any(s in line for s in ['✅ 通过', '⏳ 待测试', '⏭️ 跳过', '⚠️ 阻塞']):
        print(f'Line {i+1}: case {m.group(1)}: {line.strip()[:150]}')
