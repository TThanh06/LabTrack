from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from hashlib import sha256
import json

root = Path(__file__).resolve().parents[1]
dest = root.parent / 'labtrack-source.zip'
blocked = {'node_modules', 'target', 'data', '.git', '.m2', '.npm-cache', '.angular', 'dist', '__pycache__'}
files = []
for p in root.rglob('*'):
    rel = p.relative_to(root)
    if not p.is_file() or p.is_symlink() or any(part in blocked for part in rel.parts):
        continue
    if rel.as_posix().startswith('src/main/resources/static/') or p.name.startswith('.env') or p.suffix in {'.log', '.pem', '.key'}:
        continue
    files.append((rel.as_posix(), p))
manifest = {name: sha256(p.read_bytes()).hexdigest() for name, p in sorted(files)}
with ZipFile(dest, 'w', ZIP_DEFLATED) as z:
    for name, p in files:
        z.write(p, 'labtrack/' + name)
    z.writestr('labtrack/RELEASE-MANIFEST.json', json.dumps(manifest, indent=2))
with ZipFile(dest) as z:
    assert z.testzip() is None
    for name, digest in manifest.items():
        assert sha256(z.read('labtrack/' + name)).hexdigest() == digest
print(f'Verified {len(files)} source files: {dest}')
