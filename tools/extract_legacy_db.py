"""Estrae il DB 'Splitter' dal backup adb (.ab, non cifrato) dell'app originale.
Uso: python extract_legacy_db.py backup_dati.ab Splitter.db
"""
import sys, tarfile, zlib

src, dst = sys.argv[1], sys.argv[2]
with open(src, "rb") as f:
    hdr = [f.readline() for _ in range(4)]          # ANDROID BACKUP / version / compressed / encryption
    if hdr[0].strip() != b"ANDROID BACKUP" or hdr[3].strip() != b"none":
        sys.exit("backup non valido o cifrato")
    comp = hdr[2].strip() == b"1"
    d = zlib.decompressobj() if comp else None
    tmp = src + ".tar.tmp"
    with open(tmp, "wb") as o:
        while chunk := f.read(1 << 20):
            o.write(d.decompress(chunk) if d else chunk)
try:
    with tarfile.open(tmp) as t:
        for m in t:  # il tar puo' risultare troncato in coda: ci fermiamo appena trovato il DB
            if m.name.endswith("/db/Splitter"):
                open(dst, "wb").write(t.extractfile(m).read())
                print("scritto", dst, m.size, "byte")
                break
        else:
            sys.exit("DB Splitter non trovato")
finally:
    import os; os.remove(tmp)
