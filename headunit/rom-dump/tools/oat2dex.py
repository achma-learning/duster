"""Carve DEX files out of an Android 6 OAT/.odex (OAT 064).
ART may quicken some bytecode in place, so the stored Adler-32 can be stale: we keep the
bytes, recompute Adler-32 + SHA-1 so tools accept the file, and report it.
Usage: python -I oat2dex.py file.odex outdir"""
import sys, os, struct, zlib, hashlib, re
data = open(sys.argv[1], "rb").read(); out = sys.argv[2]; os.makedirs(out, exist_ok=True)
n = 0
for m in re.finditer(rb"dex\n03[5-9]\x00", data):
    i = m.start(); size = struct.unpack_from("<I", data, i + 0x20)[0]
    if size < 0x70 or i + size > len(data): continue
    dex = bytearray(data[i:i + size])
    stored = struct.unpack_from("<I", dex, 8)[0]
    orig_ok = (zlib.adler32(bytes(dex[12:])) & 0xffffffff) == stored
    dex[12:32] = hashlib.sha1(bytes(dex[32:])).digest()
    struct.pack_into("<I", dex, 8, zlib.adler32(bytes(dex[12:])) & 0xffffffff)
    name = "classes.dex" if n == 0 else "classes%d.dex" % (n + 1); n += 1
    open(os.path.join(out, name), "wb").write(dex)
    print("  %s %d bytes (%s)" % (name, size, "original" if orig_ok else "quickened by ART, header checksum recomputed"))
print("%d dex file(s) from %s" % (n, os.path.basename(sys.argv[1])))
