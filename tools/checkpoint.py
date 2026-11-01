#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib,sys
root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]);out.parent.mkdir(parents=True,exist_ok=True)
excluded={'build','.git','.gradle','sdk','__pycache__'}
files=sorted(p for p in root.rglob('*') if p.is_file() and not excluded.intersection(p.relative_to(root).parts) and p.suffix not in {'.apk','.idsig','.zip','.pyc'} and p.name!='SOURCE_FILES.sha256')
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 manifest=[]
 for p in files:
  data=p.read_bytes(); rel=p.relative_to(root).as_posix(); z.writestr(rel,data);manifest.append(hashlib.sha256(data).hexdigest()+'  '+rel)
 z.writestr('SOURCE_FILES.sha256','\n'.join(manifest)+'\n')
print(out);print('files',len(files),'bytes',out.stat().st_size,'sha256',hashlib.sha256(out.read_bytes()).hexdigest())
