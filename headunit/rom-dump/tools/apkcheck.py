"""Check an APK against the head unit: package, versions, minSdk, ABIs, GLES, signer.
Usage: python -I apkcheck.py <file.apk> [...]"""
import sys, zipfile, struct, subprocess, tempfile, os, re

ATTR = {0x0101020c: "minSdkVersion", 0x01010270: "targetSdkVersion",
        0x0101021b: "versionCode", 0x0101021c: "versionName", 0x01010281: "glEsVersion"}

def axml(data):
    strings, res_ids, out = [], [], {}
    pos = 8
    while pos < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if ctype == 0x0001:  # string pool
            n, _, flags, sstart, _ = struct.unpack_from("<IIIII", data, pos + 8)
            offs = struct.unpack_from("<%dI" % n, data, pos + 28)
            utf8 = flags & 0x100
            for o in offs:
                p = pos + sstart + o
                if utf8:
                    l = data[p]; p += 2 if not l & 0x80 else 3
                    l2 = data[p - 1] if not data[p - 2] & 0x80 else ((data[p - 2] & 0x7f) << 8) | data[p - 1]
                    strings.append(data[p:p + l2].decode("utf8", "replace"))
                else:
                    l = struct.unpack_from("<H", data, p)[0]
                    strings.append(data[p + 2:p + 2 + l * 2].decode("utf-16le", "replace"))
        elif ctype == 0x0180:
            res_ids = list(struct.unpack_from("<%dI" % ((csize - hsize) // 4), data, pos + hsize))
        elif ctype == 0x0102:  # start element
            name_i = struct.unpack_from("<I", data, pos + 20)[0]
            astart, asize, acount = struct.unpack_from("<HHH", data, pos + 24)
            tag = strings[name_i]
            for i in range(acount):
                a = pos + 16 + astart + i * asize
                _, aname, araw, _, _, atype, adata = struct.unpack_from("<IIIHBBI", data, a)
                rid = res_ids[aname] if aname < len(res_ids) else 0
                val = strings[araw] if araw != 0xFFFFFFFF else adata
                if tag == "manifest" and strings[aname] == "package":
                    out["package"] = val
                key = ATTR.get(rid)
                if key and tag in ("manifest", "uses-sdk", "uses-feature"):
                    if key == "glEsVersion":
                        val = "%d.%d" % (adata >> 16, adata & 0xffff)
                    out.setdefault(key, val)
        pos += csize
    return out

for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    info = axml(z.read("AndroidManifest.xml"))
    abis = sorted({n.split("/")[1] for n in z.namelist() if n.startswith("lib/") and n.count("/") >= 2})
    signer = "-"
    blocks = [n for n in z.namelist() if re.match(r"META-INF/[^/]+\.(RSA|DSA|EC)$", n)]
    if blocks:
        with tempfile.NamedTemporaryFile(delete=False) as t:
            t.write(z.read(blocks[0]))
        pem = subprocess.run(["openssl", "pkcs7", "-inform", "DER", "-in", t.name, "-print_certs"], capture_output=True, text=True).stdout
        fp = subprocess.run(["openssl", "x509", "-noout", "-fingerprint", "-sha256"], input=pem, capture_output=True, text=True).stdout
        signer = fp.split("=", 1)[-1].strip().replace(":", "").lower()
        os.unlink(t.name)
    else:
        signer = "(v2/v3 signature only - no v1 block)"
    print(os.path.basename(path))
    for k in ("package", "versionName", "versionCode", "minSdkVersion", "targetSdkVersion", "glEsVersion"):
        print("  %-16s %s" % (k, info.get(k, "-")))
    print("  %-16s %s" % ("native ABIs", ", ".join(abis) or "none (pure Java - OK)"))
    print("  %-16s %s" % ("signer sha256", signer))
    ms = info.get("minSdkVersion")
    ok_sdk = isinstance(ms, int) and ms <= 23
    ok_abi = not abis or "armeabi-v7a" in abis or "armeabi" in abis
    print("  VERDICT: %s" % ("OK for head unit" if ok_sdk and ok_abi else "NOT compatible (minSdk>23 or no 32-bit ARM)"))
