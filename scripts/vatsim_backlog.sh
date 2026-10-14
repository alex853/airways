#!/bin/bash
# VATSIM backlog report for the Airways world. Usage: bash scripts/vatsim_backlog.sh [workdir]
# Fetches the fresh vatsim-top-missing-airports list + /geo/cities + /geo/airports, skips airports already in the world
# and prints the markdown table. The token is read from admin-requests.http at runtime and is never printed.
WORK="${1:-$(mktemp -d)}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="https://d1.simforge.net:7776"
mkdir -p "$WORK" && cd "$WORK" || exit 1
AUTH=$(grep -m1 -i '^Authorization:' "$REPO/admin-requests.http" | tr -d '\r')
curl -sk -m 90 -H "$AUTH" -o vatsim_missing.txt -w "list http=%{http_code} bytes=%{size_download}\n" "$HOST/admin/flight-stats/vatsim-top-missing-airports"
curl -sk -m 60 -o cities_w.json "$HOST/geo/cities"
curl -sk -m 60 -o airports_w.json "$HOST/geo/airports"
REPO="$REPO" PYTHONIOENCODING=utf-8 python -I - <<'PYEOF'
import json,csv,math,os,collections,sys
sys.stdout.reconfigure(encoding='utf-8')
repo=os.environ['REPO']
c=json.load(open('cities_w.json',encoding='utf-8')); a={x['icao'] for x in json.load(open('airports_w.json',encoding='utf-8'))}
meta={r['icao']:r for r in csv.DictReader(open(repo+'/src/main/resources/icaodata.csv',encoding='utf-8'))}
rows=list(csv.DictReader(open(repo+'/src/main/resources/city-population.csv',encoding='utf-8')))
cs=[r for r in rows if not r['CityName'].startswith('del ')]
name2code={}
for r in rows: name2code[r['CountryName'].lower()]=r['CountryCode']
key=collections.defaultdict(list); byname=collections.defaultdict(set)
for r in rows:
    key[(r['CityName'].lower(),int(r['CityPopulation']))].append(r['CountryCode']); byname[r['CityName'].lower()].add(r['CountryCode'])
cc=collections.Counter()
for x in c:
    k=(x['name'].lower(),x['population'])
    code=key[k][0] if k in key else (list(byname[x['name'].lower()])[0] if len(byname.get(x['name'].lower(),()))==1 else None)
    if code: cc[code]+=1
extra={'Faroe Islands':'FO','Netherlands Antilles':'AN','Puerto Rico':'PR','Palestine':'PS'}
def d(a1,b1,c1,e1):
    p=math.pi/180;x=math.sin((c1-a1)*p/2)**2+math.cos(a1*p)*math.cos(c1*p)*math.sin((e1-b1)*p/2)**2
    return 12742*math.asin(math.sqrt(x))/1.852
def fmt(n): return f"{n/1e6:.1f}M" if n>=1e6 else f"{n//1000}k"
lst=[(l.split()[0],int(l.split()[1])) for l in open('vatsim_missing.txt',encoding='utf-8').read().strip().split('\n') if len(l.split())==2]
print('in list:',len(lst),'| already in world (sliding window 7d+today, counters fade by themselves):',[i for i,_ in lst if i in a])
print()
print('| ICAO | Упом. | Страна (городов в Airways) | Ближайший город в Airways | Анализ по city-population (30 nm) | >=100k | Σ 25 nm | Тип |')
print('|---|---|---|---|---|---|---|---|')
n_t1=n_t2=n_sp=0
for i,n in lst:
    if i in a: continue
    m=meta.get(i)
    if not m:
        print(f'| {i} | {n} | ? | ? | нет в icaodata (берётся из refdata) | - | - | особый |'); n_sp+=1; continue
    ctry=m['country']; code=name2code.get(ctry.lower()) or extra.get(ctry,'?')
    la,lo=float(m['lat']),float(m['lon'])
    cb=min(c,key=lambda x:d(la,lo,x['latitude'],x['longitude'])); dist=d(la,lo,cb['latitude'],cb['longitude'])
    near=[]
    for r in cs:
        dd=d(la,lo,float(r['CityLatitude']),float(r['CityLongitude']))
        if dd<30: near.append((dd,r['CityName'],int(r['CityPopulation'])))
    top=sorted(near,key=lambda x:-x[2])[:3]
    s25=sum(x[2] for x in near if x[0]<=25); nb=len([x for x in near if x[2]>=100000])
    desc=', '.join(f"{x[1]} {fmt(x[2])} ({round(x[0])})" for x in top) if top else 'нет городов в CSV'
    if dist<=10: typ='1: город есть'; n_t1+=1
    elif m['type']!='civil': typ='особый (military)'; n_sp+=1
    elif not near: typ='особый (нет в CSV)'; n_sp+=1
    else: typ='2: нужен новый город'; n_t2+=1
    print(f"| {i} | {n} | {ctry} ({cc.get(code,0)}) | {cb['name']} ({round(dist)} nm) | {desc} | {nb} | {fmt(s25) if s25 else '-'} | {typ} |")
print()
print('type 1:',n_t1,'| type 2:',n_t2,'| special:',n_sp)
PYEOF
