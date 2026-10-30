import csv,sys,math
rows=list(csv.DictReader(open(sys.argv[1])))
E=['RHINO_COMPILED','RHINO_MONFLABS_ES6_COMPILED','RHINO_MONFLABS_COMPILED']
def g(xs): return math.exp(sum(map(math.log,xs))/len(xs)) if xs else float('nan')
suites={}
allr={e:[] for e in E[1:]}
for r in rows:
    v=[r[e+' WallTime(ms)'] for e in E]
    if 'FAILED' in v: continue
    v=list(map(float,v))
    for i,e in enumerate(E[1:]):
        suites.setdefault(r['Suite'],{x:[] for x in E[1:]})[e].append(v[0]/v[i+1]); allr[e].append(v[0]/v[i+1])
for s,d in suites.items(): print(s,len(d[E[1]]),' '.join('%s x%.2f'%(e.split('_')[-2],g(d[e])) for e in E[1:]))
print('all',len(allr[E[1]]),' '.join('x%.2f'%g(allr[e]) for e in E[1:]))
# speed of ECMASCRIPT against ES6
print('ECMA/ES6 all x%.2f'%g([b/a for a,b in zip(allr[E[1]],allr[E[2]])]))
for r in rows:
    v=[r[e+' WallTime(ms)'] for e in E]
    if 'FAILED' in v: print(r['File'],v); continue
    v=list(map(float,v)); print('%-32s %8.0f %6.2f %6.2f'%(r['File'],v[0],v[0]/v[1],v[0]/v[2]))
