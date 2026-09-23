# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(__file__).parent
FONT = 'C:/Windows/Fonts/malgun.ttf'
BOLD = 'C:/Windows/Fonts/malgunbd.ttf'
SCREENS = [
 ('01-unallocated', '미할당', '가상 BMC가 할당되지 않았습니다.', [
  ('가상머신', 'vm-ipmi-demo   ·   실행 중 / KVM'),
  ('현재 호스트', 'compute-31-2   ·   연결됨 / 사용'),
  ('이 기능은 무엇인가요?', '외부 IPMI 클라이언트에서 VM 전원 상태를 조회하고 제어합니다.'),
  ('지원 조건', '실행 중인 KVM VM, HA 비활성화, 정상 호스트'),
  ('사용 시 제한', 'VM 중지·이동·삭제·복원, HA 및 인스턴스 이름 변경 전 해제 필요'),
  ('주의', 'Cloud에서 정지한 VM을 IPMI로 다시 생성·시작할 수 없습니다.')], ['닫기', '할당']),
 ('02-allocate', '할당 설정', '접속 대상 주소는 현재 호스트를 기준으로 자동 설정됩니다.', [
  ('가상머신', 'vm-ipmi-demo   ·   compute-31-2'),
  ('IPMI 사용자 이름', 'vbmc (고정)'),
  ('비밀번호 *', '● ● ● ● ● ● ● ● ● ● ● ● ● ● ● ●'),
  ('비밀번호 조건', '16~20자 ASCII 문자 · 공백과 % 제외 · 저장 후 다시 조회 불가'),
  ('접근 범위', '○ 호스트 내부만 (기본)    ● 외부 클라이언트 허용'),
  ('허용 클라이언트 IPv4 CIDR *', '10.10.0.0/16'),
  ('허용 범위 안내', '접속해 오는 클라이언트의 주소 범위입니다. VBMC 접속 주소가 아닙니다.'),
  ('동적 IP 안내', '클라이언트 IP가 바뀌어도 지정 범위 안이면 허용됩니다.')], ['취소', '할당']),
 ('03-ready', '마지막 확인 정상', '이 화면에서 상태 확인 성공 · 2026-09-23 14:32:10 (예시)', [
  ('가상머신', 'vm-ipmi-demo'),
  ('접속 주소 / UDP 포트', '10.10.31.2  /  6230'),
  ('IPMI 사용자 / 허용 클라이언트', 'vbmc  /  10.10.0.0/16'),
  ('상태 확인 범위', 'Agent가 인증·도메인·방화벽을 검사합니다. 외부 경로 검사는 별도입니다.'),
  ('접속 예시 · 비밀번호 미포함', 'ipmitool -I lanplus -H 10.10.31.2 -p 6230 -U vbmc -a chassis status'),
  ('인증정보', '비밀번호는 조회할 수 없습니다. 변경하려면 해제 후 다시 할당합니다.'),
  ('지원 제한', 'VM 중지·이동 등 구성 변경 전 가상 BMC를 먼저 해제하세요.')], ['닫기', '해제 / 재할당 ∨', '상태 확인']),
 ('04-reallocate', '해제 후 재할당', '인증정보 또는 허용 범위를 바꾸려면 기존 접속을 먼저 해제합니다.', [
  ('변경 대상', 'vm-ipmi-demo   ·   10.10.31.2:6230'),
  ('1. 기존 가상 BMC 해제', '실제 정리가 확인된 경우에만 새 할당 설정으로 이동합니다.'),
  ('2. 새 인증정보 / CIDR 입력', '다음 설정 화면에서 새 값을 입력합니다.'),
  ('접속 중단 안내', '작업 중 IPMI 접속이 끊기며, 다시 할당되는 포트는 달라질 수 있습니다.'),
  ('실패 시 처리', '해제 실패: 정리 필요 유지 / 새 할당 실패: 결과를 다시 확인'),
  ('확인', '☑ 접속 중단과 접속 정보 변경 가능성을 확인했습니다.')], ['취소', '해제 후 설정']),
 ('05-remove', '가상 BMC 해제', 'VM은 계속 실행됩니다. 외부 IPMI 접속은 종료됩니다.', [
  ('가상머신', 'vm-ipmi-demo'),
  ('해제할 접속점', '10.10.31.2:6230 / UDP'),
  ('정리 대상', '원래 할당 호스트의 VBMC 프로세스와 해당 접근 규칙'),
  ('포트 반환', '정리 성공이 확인된 경우에만 포트 할당을 반환합니다.'),
  ('정리 실패 시', '포트를 유지하고 “정리 필요”로 표시합니다. 다시 정리할 수 있습니다.')], ['취소', '해제']),
 ('06-pending', '해제 중', '호스트의 가상 BMC와 접근 규칙을 정리하고 있습니다.', [
  ('진행 상태', '진행 중…   정리 확인 → 포트 반환 → 완료'),
  ('가상머신', 'vm-ipmi-demo'),
  ('접속점', '10.10.31.2:6230 / UDP'),
  ('중복 작업 방지', '처리가 끝날 때까지 할당·상태 확인·해제 작업은 비활성화됩니다.'),
  ('창 닫기', '창을 닫아도 요청은 취소되지 않습니다. 재진입 시 최신 상태를 조회합니다.')], ['닫기']),
 ('07-cleanup', '정리 필요', '접속점 상태 또는 이전 정리 완료를 확인하지 못했습니다.', [
  ('유지 중인 할당', '10.10.31.2:6230 / UDP'),
  ('오류 안내', '원래 호스트에서 정리 완료를 확인하지 못해 포트를 유지합니다.'),
  ('다음 작업', '호스트 연결 상태를 확인한 뒤 “정리 재시도”를 실행하세요.'),
  ('보호 동작', '정리가 확인되기 전 새 할당과 VM 구성 변경은 제한됩니다.'),
  ('관리자 안내', '이전 버전 할당은 관리자 수동 정리가 필요할 수 있습니다.'),
  ('오류 상세 ∨', '진단 메시지는 펼쳐서 확인 · 긴 내용 줄바꿈 / 비밀정보 제외')], ['닫기', '정리 재시도']),
 ('08-unknown', '결과 미확인', '요청 응답을 받지 못했습니다. 성공 또는 실패로 단정할 수 없습니다.', [
  ('마지막 조회 할당', '10.10.31.2:6230 / UDP'),
  ('현재 상태', '최신 조회에 실패했습니다. 이전 정상 표시를 현재 상태로 사용하지 않습니다.'),
  ('안내', '다시 조회하여 서버 상태와 비동기 작업 결과를 확인하세요.'),
  ('중복 실행 방지', '결과 확인 전 할당·해제를 다시 요청하지 않습니다.')], ['닫기', '다시 조회']),
 ('09-ineligible', '할당할 수 없음', '이 VM은 HA가 활성화되어 있어 가상 BMC를 할당할 수 없습니다.', [
  ('가상머신', 'vm-ha-demo   ·   실행 중 / KVM'),
  ('지원 조건 확인', '✓ KVM     ✓ 실행 중     ✓ 호스트 정상     ✕ HA 비활성화'),
  ('제한 사유', 'HA로 호스트가 변경되면 기존 접속점을 유지할 수 없습니다.'),
  ('다른 제한', '지원하지 않는 하이퍼바이저·VM 상태·호스트 상태도 사유를 안내합니다.'),
  ('권한', '작업 권한이 없으면 조회 가능 범위 내에서 안내하고 실행은 차단합니다.')], ['닫기', '할당 불가']),
]

def render(name, status, note, rows, buttons, light=False):
    bg, panel, field, line, fg, muted = ('#eef1f5','#ffffff','#f5f7fa','#c5ced8','#202b38','#46586b') if light else ('#171e24','#252e37','#1d252d','#526170','#edf3f9','#c1cfdd')
    im=Image.new('RGB',(1440,1060),bg); d=ImageDraw.Draw(im)
    def txt(x,y,s,size=18,c=fg,b=False): d.text((x,y),s,font=ImageFont.truetype(BOLD if b else FONT,size),fill=c)
    def box(r,fill=panel,outline=line): d.rounded_rectangle(r,8,fill=fill,outline=outline,width=1)
    txt(38,25,'ABLESTACK  /  가상머신  /  vm-ipmi-demo',20,b=True)
    txt(38,70,'설계 목업 · #1160 · '+('라이트 모드' if light else '다크 모드'),16,c=muted)
    box((34,130,298,1000)); txt(56,155,'vm-ipmi-demo',23,b=True)
    for i,s in enumerate(['실행 중','4 CPU · 8 GiB','KVM','현재 호스트','compute-31-2','가상 BMC(IPMI)','관리 화면 열기 →']): txt(56,220+i*48,s,17,c=muted)
    box((322,130,1404,1000)); txt(347,155,'상세     IP 구성     메트릭     스케줄',17,c=muted)
    # Fixed frame: title and footer outside scrollable body.
    im.paste(Image.blend(im, Image.new("RGB", im.size, "#000000"), 0.45))
    d=ImageDraw.Draw(im)
    x,y,w,h=220,175,1000,710
    box((x,y,x+w,y+h)); txt(x+28,y+22,'가상 BMC(IPMI) 관리',25,b=True); txt(x+w-48,y+22,'×',25,c=muted)
    d.line((x,y+75,x+w,y+75),fill=line)
    txt(x+28,y+95,status,23,c=('#866000' if light else '#ffdc88') if name not in ['03-ready'] else ('#126e4b' if light else '#8ce6bd'),b=True)
    txt(x+28,y+134,note,17,c=muted)
    yy=y+183
    for label,val in rows:
        txt(x+28,yy,label,16,c=muted,b=True)
        if name == '02-allocate' and label in ['비밀번호 *', '허용 클라이언트 IPv4 CIDR *']:
            box((x+26,yy+23,x+w-30,yy+53),field)
        txt(x+32,yy+25,val,18); yy+=53
    d.line((x,y+h-78,x+w,y+h-78),fill=line)
    bx=x+w-28
    for j,label in enumerate(reversed(buttons)):
        bw=max(90,int(d.textlength(label,font=ImageFont.truetype(FONT,18)))+34)
        primary=j==0 and len(buttons)>1 and label!='할당 불가'
        box((bx-bw,y+h-58,bx,y+h-16), '#176bc2' if primary else field)
        txt(bx-bw+17,y+h-50,label,18,c='#ffffff' if primary else muted); bx-=bw+12
    txt(350,922,'중앙 배치  ·  제목 / 하단 버튼 고정  ·  긴 본문만 스크롤',18,c=muted)
    txt(350,958,'주소·시각·이름은 예시입니다. 비밀번호는 표시하거나 복사하지 않습니다.',16,c=muted)
    im.save(OUT/(name+('-light' if light else '-dark')+'.png'))

for s in SCREENS: render(*s)
for s in [SCREENS[1],SCREENS[2],SCREENS[6]]: render(*s,light=True)

# Compact viewport: the body is clipped inside its own scroll region.
im=Image.new('RGB',(900,700),'#171e24'); d=ImageDraw.Draw(im)
f=lambda n:ImageFont.truetype(FONT,n)
d.rounded_rectangle((50,40,850,660),8,fill='#252e37',outline='#526170')
d.text((78,64),'가상 BMC(IPMI) 관리',font=f(24),fill='#edf3f9')
d.line((50,115,850,115),fill='#526170')
body=Image.new('RGB',(744,462),'#252e37'); bd=ImageDraw.Draw(body)
lines=['할당 설정','가상머신: vm-with-a-very-long-name-for-ipmi-management',
 '긴 이름은 줄바꿈 또는 툴팁으로 전체 내용을 확인합니다.',
 'IPMI 사용자 이름: vbmc (고정)','비밀번호 *',
 '● ● ● ● ● ● ● ● ● ● ● ● ● ● ● ●',
 '16~20자 ASCII · 공백과 % 제외','접근 범위: 외부 클라이언트 허용',
 '허용 클라이언트 IPv4 CIDR *','10.10.0.0/16',
 '클라이언트가 접속해 오는 주소 범위입니다.',
 '지정 범위 내 동적 IP는 허용됩니다.']
for i,t in enumerate(lines): bd.text((0,i*46),t,font=f(19),fill='#edf3f9')
im.paste(body,(78,134)); d=ImageDraw.Draw(im)
d.rounded_rectangle((831,133,837,588),3,fill='#1d252d')
d.rounded_rectangle((831,134,837,384),3,fill='#c1cfdd')
d.line((50,595,850,595),fill='#526170')
for x,t,c in [(628,'취소','#1d252d'),(732,'할당','#176bc2')]:
 d.rounded_rectangle((x,609,x+90,649),5,fill=c,outline='#526170'); d.text((x+23,616),t,font=f(18),fill='#edf3f9')
im.save(OUT/'10-compact-scroll-dark.png')
