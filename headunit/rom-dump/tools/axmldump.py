"""Pretty-print Android binary XML (manifest / res xml). Usage: python -I axmldump.py file.xml"""
import sys, struct
NAMES = {0x01010003:"name",0x01010001:"label",0x01010002:"icon",0x0101000f:"permission",0x01010010:"exported",
 0x0101001d:"launchMode",0x0101001e:"screenOrientation",0x0101001f:"configChanges",0x01010000:"theme",
 0x0101020c:"minSdkVersion",0x01010270:"targetSdkVersion",0x0101021b:"versionCode",0x0101021c:"versionName",
 0x0101000e:"enabled",0x01010280:"hardwareAccelerated",0x01010225:"settingsActivity",0x01010018:"excludeFromRecents",
 0x01010020:"stateNotNeeded",0x0101002b:"priority",0x0101022b:"windowSoftInputMode",0x01010011:"process",
 0x01010004:"value",0x01010024:"resource",0x01010013:"clearTaskOnLaunch",0x01010015:"alwaysRetainTaskState",
 0x01010019:"finishOnTaskLaunch",0x0101001a:"noHistory",0x01010012:"taskAffinity",0x010103d0:"resizeableActivity",
 0x0101029d:"largeHeap",0x01010472:"usesCleartextTraffic",0x0101000c:"allowBackup",0x010102b7:"installLocation"}
def run(path):
    d = open(path,"rb").read(); pos = 8; S=[]; R=[]; depth=0
    while pos < len(d):
        t,h,sz = struct.unpack_from("<HHI",d,pos)
        if t==1:
            n,_,fl,ss,_ = struct.unpack_from("<IIIII",d,pos+8); offs=struct.unpack_from("<%dI"%n,d,pos+28)
            for o in offs:
                p=pos+ss+o
                if fl&0x100:
                    l=d[p]; p+=1
                    if l&0x80: p+=1
                    l2=d[p]; p+=1
                    if l2&0x80: l2=((l2&0x7f)<<8)|d[p]; p+=1
                    S.append(d[p:p+l2].decode("utf8","replace"))
                else:
                    l=struct.unpack_from("<H",d,p)[0]; S.append(d[p+2:p+2+l*2].decode("utf-16le","replace"))
        elif t==0x180: R=list(struct.unpack_from("<%dI"%((sz-h)//4),d,pos+h))
        elif t==0x102:
            ni=struct.unpack_from("<I",d,pos+20)[0]; ast,asz,ac=struct.unpack_from("<HHH",d,pos+24)
            attrs=[]
            for i in range(ac):
                a=pos+16+ast+i*asz; _,an,ar,_,_,at,ad=struct.unpack_from("<IIIHBBI",d,a)
                rid=R[an] if an<len(R) else 0; nm=NAMES.get(rid, S[an] if S[an] else "0x%08x"%rid)
                if ar!=0xFFFFFFFF: v=S[ar]
                elif at==0x12: v="true" if ad else "false"
                elif at==0x01: v="@0x%08x"%ad
                elif at in (0x10,0x11): v=str(ad) if at==0x10 else "0x%x"%ad
                else: v="(t%x)%d"%(at,ad)
                attrs.append('%s="%s"'%(nm,v))
            print("  "*depth+"<"+S[ni]+(" "+" ".join(attrs) if attrs else "")+">"); depth+=1
        elif t==0x103: depth-=1
        pos+=sz
run(sys.argv[1])
