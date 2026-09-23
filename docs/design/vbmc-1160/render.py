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

def render(name, status, note, rows, buttons, light=False, compact=False):
    bg,panel,field,line,fg,muted=('#e9edf2','#ffffff','#f2f5f8','#c9d2dd','#192c40','#465a70') if light else ('#12191f','#242e38','#1a242e','#46596b','#edf4fc','#bdcede')
    W,H=(900,740) if compact else (1440,1060)
    im=Image.new('RGB',(W,H),bg); d=ImageDraw.Draw(im)
    font=lambda n,b=False:ImageFont.truetype(BOLD if b else FONT,n)
    def text(x,y,t,n=18,c=fg,b=False): d.text((x,y),t,font=font(n,b),fill=c)
    def box(rect,fill=panel,outline=line,r=7): d.rounded_rectangle(rect,r,fill=fill,outline=outline)
    def wrap(t,width,n=17):
        out=[]; cur=''
        for ch in t:
            if d.textlength(cur+ch,font=font(n))>width: out.append(cur);cur=ch
            else: cur+=ch
        return out+[cur]
    text(30,24,'ABLESTACK  /  가상머신  /  vm-ipmi-demo',19,b=True)
    text(30,68,'#1160  ·  UI 디자인 개정안  ·  '+('라이트' if light else '다크'),15,c=muted)
    x=40 if compact else 250; w=W-2*x; h=660 if compact else 824; y=(H-h)//2
    box((x,y,x+w,y+h));text(x+28,y+22,'가상 BMC(IPMI) 관리',24,b=True);text(x+w-45,y+22,'×',24,c=muted)
    d.line((x,y+76,x+w,y+76),fill=line)
    # Status banner has a separate semantic surface.
    color=('#17684b' if light else '#97e5c2') if name=='03-ready' else ('#765100' if light else '#ffdb91')
    banner=('#eaf5f0' if light else '#203e36') if name=='03-ready' else ('#fff6e2' if light else '#3a3426')
    yy=y+96; box((x+28,yy,x+w-28,yy+88),banner,banner)
    text(x+46,yy+11,status,21,c=color,b=True)
    for i,t in enumerate(wrap(note,w-100,16)):text(x+46,yy+44+i*22,t,16,c=fg)
    yy+=108
    if name=='02-allocate':
        text(x+28,yy,'접속 대상',18,b=True);yy+=32
        box((x+28,yy,x+w-28,yy+54),field)
        text(x+44,yy+16,'가상머신',16,c=muted);text(x+208,yy+16,'vm-ipmi-demo  /  compute-31-2',17)
        yy+=76
        def form(label,value,helper=None,active=False):
            nonlocal yy
            text(x+28,yy+12,label,17,b=True)
            box((x+208,yy,x+w-28,yy+44),field,'#409dff' if active else line)
            text(x+224,yy+10,value,17)
            yy+=52
            if helper:
                for t in wrap(helper,w-244,15):text(x+208,yy,t,15,c=muted);yy+=22
                yy+=6
        form('IPMI 사용자','vbmc  (고정)')
        form('비밀번호 *','● ● ● ● ● ● ● ● ● ● ● ● ● ● ● ●','16~20자 ASCII · 공백과 % 제외 · 저장 후 조회 불가')
        text(x+28,yy+10,'접근 범위',17,b=True)
        d.ellipse((x+208,yy+13,x+222,yy+27),outline=muted,width=1)
        text(x+231,yy+8,'호스트 내부만',17)
        d.ellipse((x+385,yy+13,x+399,yy+27),outline='#409dff',width=2)
        d.ellipse((x+389,yy+17,x+395,yy+23),fill='#409dff')
        text(x+408,yy+8,'외부 클라이언트 허용',17); yy+=54
        form('클라이언트 CIDR *','10.10.0.0/16','접속해 오는 클라이언트의 IPv4 범위입니다.',True)
        if not compact:
            box((x+208,yy,x+w-28,yy+68),field)
            text(x+224,yy+10,'동적 IP도 지정한 범위 안이면 허용됩니다.',16,c=muted)
            text(x+224,yy+34,'VBMC 접속 대상 주소와는 별도 설정입니다.',16,c=muted)
    else:
        text(x+28,yy,'접속 정보' if name=='03-ready' else '관리 정보',18,b=True); yy+=33
        # Description table: stable label column, borders and wrapped values.
        for label,val in rows:
            if '접속 예시' in label:
                yy+=14;text(x+28,yy,'접속 명령',17,b=True);yy+=29
                box((x+28,yy,x+w-28,yy+72),field)
                for i,t in enumerate(wrap(val,w-90,16)):text(x+44,yy+12+i*24,t,16)
                yy+=86;continue
            lines=wrap(val,w-280,16);rh=max(48,20+max(len(lines),len(wrap(label,168,16)))*23)
            d.rectangle((x+28,yy,x+220,yy+rh),fill=field,outline=line)
            d.rectangle((x+220,yy,x+w-28,yy+rh),fill=panel,outline=line)
            for i,t in enumerate(wrap(label,168,16)):text(x+42,yy+12+i*22,t,16,c=muted,b=True)
            for i,t in enumerate(lines):text(x+237,yy+12+i*23,t,16)
            yy+=rh
    # Footer is always a distinct fixed region.
    fy=y+h-80
    d.rectangle((x+1,fy,x+w-1,y+h-8),fill=panel)
    d.line((x,fy,x+w,fy),fill=line);bx=x+w-28
    for j,label in enumerate(reversed(buttons)):
        bw=max(88,int(d.textlength(label,font=font(17)))+32);primary=j==0 and len(buttons)>1 and label!='할당 불가'
        box((bx-bw,fy+20,bx,fy+60),'#176bc2' if primary else field)
        text(bx-bw+16,fy+29,label,17,c='#ffffff' if primary else muted);bx-=bw+12
    if compact:
        d.rounded_rectangle((x+w-13,y+96,x+w-7,fy-12),3,fill=field)
        d.rounded_rectangle((x+w-13,y+96,x+w-7,y+310),3,fill=muted)
    else:
        text(x,H-77,'화면 중앙 정렬  ·  제목과 버튼 고정  ·  본문만 스크롤',16,c=muted)
        text(x,H-48,'정적 설계 목업 / 표시된 이름·주소·시각은 예시',14,c=muted)
    im.save(OUT/('10-compact-scroll-dark.png' if compact else name+('-light' if light else '-dark')+'.png'))

for s in SCREENS: render(*s)
for s in [SCREENS[1],SCREENS[2],SCREENS[6]]:render(*s,light=True)
render(*SCREENS[1],compact=True)
