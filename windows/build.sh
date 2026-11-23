#!/bin/bash
# Makes SimpleScript.ps1 (the Windows version) from windows/SimpleScript.template.ps1, copying in the
# device list and debloat list from SimpleScript.sh so both versions always offer the same things.
# Run it after changing either file:  bash windows/build.sh
set -e
cd "$(dirname "$0")/.."
python3 - <<'PY'
import re
src = open('SimpleScript.sh', encoding='utf-8').read()

def bash_array(name):
    out, inside = [], False
    for line in src.splitlines():
        t = line.strip()
        if not inside:
            inside = t == name + '=('
            continue
        if t == ')':
            break
        if len(t) >= 2 and t[0] == '"' and t[-1] == '"':
            out.append(t[1:-1].replace('\\"', '"'))
    return out

def value(name):
    m = re.search(r'^' + name + r'="([^"]*)"', src, re.M)
    return m.group(1) if m else ''

def ps(s):
    return "'" + s.replace("'", "''") + "'"

def ps_array(var, items):
    return '$' + var + ' = @(\n' + ',\n'.join('    ' + ps(i) for i in items) + '\n)'

lists = '\n'.join([
    '# ---- Copied from SimpleScript.sh by windows/build.sh ----',
    '$ScriptVersion = ' + ps(value('SCRIPT_VERSION')),
    '$Repo = ' + ps(value('REPO')),
    '$RepoUrl = "https://github.com/$Repo"',
    '',
    '# Supported devices: model code | name | Fire OS version tested | extras | HomeCatch',
    ps_array('SupportedDevices', bash_array('SUPPORTED_DEVICES')),
    '',
    '# Devices being tested: HomeCatch only. model code | name | Fire OS version (* = any)',
    ps_array('TestingDevices', bash_array('TESTING_DEVICES')),
    '$UpdaterPkg = ' + ps(value('UPDATER_PKG')),
    '',
    ps_array('DebloatGroups', bash_array('DEBLOAT_GROUPS')),
    '',
    ps_array('DebloatList', bash_array('DEBLOAT_LIST')),
    '# ---- End of copied lists ----',
])
template = open('windows/SimpleScript.template.ps1', encoding='utf-8').read()
assert '# @@LISTS@@' in template
out = template.replace('# @@LISTS@@', lists).replace('\r\n', '\n').replace('\n', '\r\n')
# UTF-8 with a byte-order mark, so Windows PowerShell 5.1 reads the symbols correctly.
open('SimpleScript.ps1', 'w', encoding='utf-8-sig', newline='').write(out)
print('Built SimpleScript.ps1')
PY
