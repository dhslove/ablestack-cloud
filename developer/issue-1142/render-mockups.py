# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
P=Path(__file__).resolve().parent
FONT='/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc'
def render(mode):
 im=Image.new('RGB',(1600,1000),'#171c21');d=ImageDraw.Draw(im)
 fg='#d3dae2'; muted='#a9b4c0'; border='#404952'; blue='#2495ff'; panel='#222a31'
 def rect(box,fill,outline=None,r=0):
  if r:d.rounded_rectangle(box,r,fill=fill,outline=outline)
  else:d.rectangle(box,fill=fill,outline=outline)
 def t(x,y,s,size=18,c=fg):
  s=s.replace('☰','≡').replace('⌂','홈').replace('⌕','검색').replace('▾','')
  d.text((x,y),s,font=ImageFont.truetype(FONT,size),fill=c)
 def b(x,y,w,s,primary=False,danger=False):
  rect((x,y,x+w,y+38),'#ab3843' if danger else blue if primary else '#28323b',None if primary else border,5);t(x+14,y+5,s,16,'#ffffff' if primary or danger else fg)
  if '▾' in s:d.line([(x+w-20,y+16),(x+w-15,y+21),(x+w-10,y+16)],fill=fg,width=2)
 rect((0,0,1600,62),'#202830');t(30,17,'☰   ABLESTACK  Mold',21);t(1280,20,'기본 보기     admin cloud',16)
 t(30,87,'⌂  /  가상머신  /  W2025-Base',21);b(1440,78,125,'작업  ▾')
 rect((24,142,360,958),panel,border,5);t(52,171,'W2025-Base',26);t(52,217,'i-2-165-VM     KVM     x86_64',15,muted)
 for i,(a,c) in enumerate([('상태','●  실행 중'),('CPU','4 CPU × 1.00 GHz'),('메모리','8192 MB'),('IP 주소','10.10.254.167'),('네트워크','l2-net'),('컴퓨트 오퍼링','custom'),('호스트','ablecube3')]):
  y=276+i*79;t(52,y,a,15,muted);t(52,y+26,c,18,blue if a in ['IP 주소','네트워크'] else fg)
 rect((382,142,1576,958),panel,border,5)
 tabs=['상세','메트릭','ISO','볼륨','NIC','IP 구성','VM 스냅샷','백업','DR 계획','장애보호','스케줄','장치','설정']
 for i,s in enumerate(tabs):
  y=173+i*54
  if s=='백업':rect((397,y-7,548,y+36),'#193b57');rect((546,y-7,549,y+36),blue)
  t(417,y,s,17,blue if s=='백업' else muted)
 d.line((550,164,550,928),fill=border)
 b(577,173,140,'＋ 백업 생성',True);b(729,173,125,'새로고침');b(866,173,140,'백업 설정 ▾');rect((1165,173,1547,211),'#20272e',border,5);t(1180,181,'백업 이름 검색                         ⌕',16,muted)
 t(578,231,'백업 오퍼링: 미지정' if mode=='empty' else '백업 오퍼링: daily-protection   ·   공급자: KBOSS',16,muted)
 if mode=='empty':
  rect((577,173,717,211),'#35404a',border,5);t(590,180,'＋ 백업 생성',16,'#77828d')
 if mode=='empty':
  rect((577,278,1547,365),'#293342',border,5);t(601,294,'백업 오퍼링을 먼저 지정하세요.',21);t(601,330,'오퍼링을 지정하면 백업 생성과 스케줄 설정을 사용할 수 있습니다.',16,muted)
  b(1265,290,255,'백업 오퍼링 지정',True);t(940,509,'아직 생성된 백업이 없습니다.',21,muted)
 else:
  headers=[(589,'이름'),(835,'상태'),(947,'크기 / 가상 크기'),(1137,'유형 / 주기'),(1330,'생성일'),(1460,'작업')]
  for x,s in headers:t(x,283,s,15,muted)
  d.line((577,320,1547,320),fill=border)
  rows=[('daily-20260921','● 완료','23.6 / 100 GB','전체 / 매일','09.21 02:00'),('manual-before-update','● 완료','5.2 / 100 GB','증분 / 수동','09.20 18:30'),('daily-20260920','◌ 생성 중','— / 100 GB','증분 / 매일','09.20 02:00')]
  for i,row in enumerate(rows):
   y=340+i*94
   for x,s in zip([589,835,947,1137,1330],row):t(x,y,s,16,blue if x==589 else '#83d99c' if x==835 and i<2 else fg)
   if i<2:t(589,y+30,'압축 완료  ·  검증 완료',14,muted)
   b(1452,y-5,58,'복원');b(1518,y-5,30,'▾');d.line((577,y+70,1547,y+70),fill=border)
  t(1207,646,'전체 3개     ‹   1   ›     10 / 쪽',16,muted)
  if mode=='list':
   rect((1265,385,1546,606),'#2b343e',border,6)
   for i,s in enumerate(['상세 보기','볼륨 복원 및 VM에 연결','백업에서 새 VM 생성','백업 삭제']):t(1283,405+i*47,s,17,'#ff969e' if i==3 else fg)
   t(577,777,'목록은 10초마다 자동 갱신합니다. 작업 중에도 현재 목록을 유지합니다.',16,muted)
   t(577,811,'작업은 권한·백업 상태·공급자 지원 여부에 따라 표시됩니다.',16,muted)
 if mode=='restore':
  shade=Image.new('RGBA',im.size,(0,0,0,130));im=Image.alpha_composite(im.convert('RGBA'),shade).convert('RGB');d=ImageDraw.Draw(im)
  rect((510,178,1160,796),'#222a31',border,7);t(538,198,'백업 복원',23);t(1114,199,'×',24,muted);d.line((510,247,1160,247),fill=border)
  for i,(a,v) in enumerate([('대상 VM','W2025-Base'),('백업','daily-20260921'),('백업 상태','BackedUp'),('생성일','2026. 9. 21. 오전 2:00')]):
   y=270+i*48;rect((538,y,1132,y+48),'#26313a',border);t(552,y+12,a,16,muted);t(728,y+12,v,17)
  t(538,490,'빠른 복원',18);rect((1069,494,1129,520),'#53626e',None,13);d.ellipse((1072,497,1092,517),fill='#dde4eb')
  t(538,541,'호스트 (관리자)',17);rect((538,577,1132,618),'#20272e',border,4);t(551,585,'자동 선택                                           ▾',16,muted)
  rect((538,638,1132,707),'#3a3024','#735b35',4);t(553,649,'백업 시점으로 복원하면 이후의 VM 변경 사항을 잃을 수 있습니다.',16,'#f5d299');t(553,675,'대상 VM과 백업을 확인한 후 진행하세요.',16,'#f5d299');d.line([(1103,594),(1109,600),(1115,594)],fill=fg,width=2)
  b(925,739,82,'취소');b(1015,739,117,'백업 복원',True)
 rect((0,966,1600,1000),'#11283d');t(25,972,'검토용 목업 · 예시 데이터 · 구현 전  |  #1142  VM 백업 탭',16,'#9fc9ee')
 im.save(P/(mode+'.png'))
for m in ['list','restore','empty']:render(m)
