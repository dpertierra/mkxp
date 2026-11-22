#!/usr/bin/env python3
"""Same output as `xxd -i -n NAME FILE`, without needing xxd's -n option
(missing from the xxd shipped with Ubuntu 22.04)."""
import sys

name, path = sys.argv[1], sys.argv[2]
with open(path, 'rb') as f:
    data = f.read()

out = ['unsigned char %s[] = {\n' % name]
for i in range(0, len(data), 12):
    out.append('  ' + ', '.join('0x%02x' % b for b in data[i:i + 12]))
    out.append(',\n' if i + 12 < len(data) else '\n')
out.append('};\nunsigned int %s_len = %d;\n' % (name, len(data)))
sys.stdout.write(''.join(out))
