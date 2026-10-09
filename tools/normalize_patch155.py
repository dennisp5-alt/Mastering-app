import re,sys,gzip,subprocess
raw = gzip.decompress(open(sys.argv[1],'rb').read()).decode('utf-8')
lines = raw.splitlines(keepends=True)
out=[];i=0;count=0
while i < len(lines):
    line=lines[i]
    if line.startswith('@@ '):
        m=re.match(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)$',line.rstrip('\n'))
        if not m:raise ValueError('malformed hunk header '+line)
        j=i+1
        while j < len(lines) and not lines[j].startswith('@@ ') and not (lines[j].startswith('--- ') and j+1<len(lines) and lines[j+1].startswith('+++ ')):
            j+=1
        block=lines[i+1:j]
        if any((not x) or x[0] not in ' +-\\' for x in block):raise ValueError('bad patch line')
        old=sum(x[0] in ' -' for x in block)
        new=sum(x[0] in ' +' for x in block)
        out.append(f'@@ -{m.group(1)},{old} +{m.group(2)},{new} @@{m.group(3)}\n')
        out.extend(block)
        i=j;count+=1
    else:
        out.append(line);i+=1
print('Recounted',count,'hunks')
r=subprocess.run(['patch','--batch','--fuzz=0','-p1','-d',sys.argv[2]],input=''.join(out),text=True,capture_output=True)
print(r.stdout);print(r.stderr)
sys.exit(r.returncode)
