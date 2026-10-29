import csv,math,sys,collections
rows=list(csv.DictReader(open(sys.argv[1])))
cols=["compile alloc KB","retained compiled KB","alloc per run KB","retained after run KB","metaspace KB","classes"]
d=collections.defaultdict(dict)
for r in rows: d[(r['suite'],r['file'])][r['engine']]=r
groups={'compiled':['RHINO_COMPILED','RHINO_MONFLABS_ES6_COMPILED','RHINO_MONFLABS_COMPILED'],
        'interpreted':['RHINO_INTERPRETED','RHINO_MONFLABS_ES6_INTERPRETED','RHINO_MONFLABS_INTERPRETED']}
print("engine footprint (empty script)")
for e,r in d[('engine','empty')].items(): print(f"  {e:32}",[r[c] for c in cols])
for g,es in groups.items():
    print("==",g)
    keys=[k for k in d if k[0]!='engine' and all(d[k].get(e,{}).get('status')=='OK' for e in es)]
    print("  files compared:",len(keys))
    for c in cols:
        out=[]
        for e in es[1:]:
            lr=[math.log(max(1,float(d[k][e][c]))/max(1,float(d[k][es[0]][c]))) for k in keys]
            out.append(f"{e}: x{math.exp(sum(lr)/len(lr)):.2f}")
        tot=[sum(float(d[k][e][c]) for k in keys) for e in es]
        print(f"  {c:24} geomean vs stock: "+"; ".join(out)+"  | totals "+" / ".join(f"{t/1024:.0f}MB" if 'KB' in c else f"{t:.0f}" for t in tot))
