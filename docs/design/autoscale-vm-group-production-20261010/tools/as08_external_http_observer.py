# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Read-only bounded AS-08 observation collector. Credentials arrive once on stdin.

import os,sys,time,json,datetime,hashlib,urllib.request,threading,signal
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
os.umask(0o077)
cfg=json.loads(sys.stdin.readline());out=Path(cfg['output']);out.mkdir(parents=True,exist_ok=True);os.chmod(out,0o700)
start=cfg['startEpoch'];deadline=cfg['deadlineEpoch'];stop=threading.Event();status={'phase':'WAITING','pid':os.getpid(),'startEpoch':start,'deadlineEpoch':deadline,'http':{},'maxGapSeconds':0,'anomalyCount':0,'origin':'WSL rocky outside management servers'}
def stamp():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def record(kind,data):
 with (out/(kind+'.jsonl')).open('a') as f:f.write(json.dumps({'at':stamp(),'epoch':time.time(),**data})+'\n')
def save():
 status['updatedAt']=stamp();t=out/'status.tmp';t.write_text(json.dumps(status,indent=2));t.replace(out/'status.json')
def probe(t):
 begin=time.monotonic();d={'target':t['name']}
 try:
  with urllib.request.urlopen(t['url'],timeout=3) as r:b=r.read(65536);d.update(status=r.status,length=len(b),sha256=hashlib.sha256(b).hexdigest());d['ok']=r.status==200 and d['sha256']==t['bodySha256']
 except Exception as e:d.update(ok=False,error=type(e).__name__)
 d['latencyMs']=round(1000*(time.monotonic()-begin),2);return d
signal.signal(signal.SIGTERM,lambda *_:stop.set());signal.signal(signal.SIGINT,lambda *_:stop.set())
save()
while time.time()<start and not stop.is_set():stop.wait(min(1,start-time.time()))
status['phase']='OBSERVING';last=None;tick=time.monotonic()
try:
 with ThreadPoolExecutor(max_workers=3) as pool:
  while time.time()<=deadline and not stop.is_set():
   now=time.time()
   if last is not None:
    gap=now-last;status['maxGapSeconds']=max(status['maxGapSeconds'],round(gap,3))
    if gap>10:status['anomalyCount']+=1;record('anomalies',{'code':'EXTERNAL_HTTP_COLLECTION_GAP','gapSeconds':gap})
   last=now
   for d in pool.map(probe,cfg['httpTargets']):
    c=status['http'].setdefault(d['target'],{'requests':0,'ok':0,'failed':0});c['requests']+=1;c['ok' if d['ok'] else 'failed']+=1;record('http',d)
    if not d['ok']:status['anomalyCount']+=1;record('anomalies',{'code':'APPLICATION_HTTP_FAILURE',**d})
   save();tick+=5;stop.wait(max(0,tick-time.monotonic()))
finally:
 status['phase']='FINISHED' if time.time()>=deadline else 'STOPPED';status['finishedAt']=stamp();save()
