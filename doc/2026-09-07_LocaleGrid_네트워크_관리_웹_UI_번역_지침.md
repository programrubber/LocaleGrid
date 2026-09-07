# 네트워크 관리 웹 UI 번역 지침

네트워크 관리 화면의 문구를 **대상과 기능, 설정과 실제 상태, 항목명과 동작을 혼동하지 않도록** 번역한다. 자동·라벨형·문장형은 표현 형식을 선택하는 옵션이며, 원문의 의미나 동작을 바꾸는 옵션이 아니다.

이 문서는 제공된 방화벽 예시에서 일반화한 번역 지침이다. 특정 제품의 문구를 정답으로 고정하지 않는다. 공통 네트워크 규칙은 TranslationSuggestionService의 시스템 프롬프트에, 모드별 규칙은 TranslationStyle에 반영했다. UI의 자동·라벨형·문장형 선택값과 연결되며, 요청에는 선택한 모드의 지침만 포함한다. 버전 상향·릴리즈는 수행하지 않았다.

## 자동·라벨형·문장형 요약

| 모드 | 지침 |
| --- | --- |
| **자동** | 네트워크 관리 화면의 문맥에 맞춰 원문의 표현 형태와 의도를 유지한다. 항목명·동작·상태·문장·질문·경고의 역할을 임의로 바꾸지 않는다. |
| **라벨형** | 네트워크 UI의 역할에 맞는 라벨로 번역한다. 항목명·속성명·컬럼은 명사구, 실행 버튼은 동작형, 실제 상태 표시는 원문에 주어진 상태값으로 표현한다. 설정 컨트롤은 기능이나 설정 동작이 명확하게 드러나도록 표현한다. UI 역할이 불명확하면 중립적인 항목명으로 번역한다. 대상·조건·부정·범위를 생략하거나 원문에 없는 상태를 단정하지 않는다. |
| **문장형** | 네트워크 UI의 설명·질문·경고·로그 등 원문의 역할을 유지하면서 자연스러운 완결 문장으로 번역한다. 기술적 의미와 조건을 보존하고, 원문에 없는 원인·동작·위험·효과·행위자·성공 여부를 추가하지 않는다. |

**공통:** 네트워크 용어, 설정과 실제 상태의 차이, 대상·조건·부정·범위, 플레이스홀더를 보존한다. 의미 보존을 문장 완성이나 길이 단축보다 우선한다. 라벨형은 요약형이 아니다.

참조 대화 [문장형 지침 다듬기](chatgpt-conversation://6a9ecac1-7f68-83e8-923a-e497fa624130)의 최종 라벨형·문장형 제안을 반영했다. 예시는 고정 번역으로 사용하지 않으며, 독립적인 완결 문장이 아닌 `Whether …` 절과 실제 값이 없는 상태값 예시는 아래 기준으로 보완했다.


## 1. 적용 범위와 우선순위

라우터, 스위치, 무선 네트워크, VPN, 인터페이스, 라우팅, NAT, ACL, HA, 모니터링, 로그와 네트워크 보안 관리 웹 UI에 적용한다. 로그인·계정·저장·취소 같은 공통 UI도 포함한다. 일반 기능을 임의로 방화벽 기능으로 해석하거나, 원문에 없는 패킷 처리·보안 효과를 덧붙이지 않는다.

판단 우선순위는 다음과 같다.

1. 원문의 의미, 긍정·부정, 조건, 대상과 기술적 동작 보존
2. 플레이스홀더, 식별자, 수치, 단위, 프로토콜 및 제품 용어 보존
3. 확인된 UI 용도와 제품 용어집 준수
4. 선택한 번역 스타일 준수
5. 자연스러움과 표기 일관성

짧은 표현보다 정확한 표현을 우선한다. 길이 제한이나 일괄 축약을 적용하지 않는다. 다만 의미에 영향을 주지 않는 한국어식 표현을 영어로 직역할 필요는 없다.

## 2. 번역 전에 구분할 것

| 구분 | 확인할 내용 | 혼동하면 안 되는 대상 |
| --- | --- | --- |
| 대상 | 인터페이스, 세션, 정책, 경로, 장비, 계정 중 무엇인가 | 정책과 규칙, 연결과 세션을 무조건 같은 말로 처리하지 않음 |
| UI 역할 | 컬럼·속성명 / 버튼·토글 / 현재 상태값 / 설명 / 경고 / 로그 | 항목명을 현재 상태의 단정으로, 설명을 실행 명령으로 바꾸지 않음 |
| 상태의 종류 | 관리자가 지정한 설정 / 실제 운영 상태 / 연결 상태 / 동기화 상태 | 설정이 켜졌다고 실제 동작 중이라고 표현하지 않음 |
| 조건·범위 | 일부/전체, 송신/수신, 출발지/목적지, 가능/필수, 사용/미사용 | 방향, 범위, 부정, 조건을 생략하지 않음 |

확인된 화면 용도와 원문을 먼저 사용하고, 키 이름과 다른 언어의 참조 번역은 보조 근거로 사용한다. `testValues`처럼 용도를 설명하지 않는 키에서는 화면 역할을 추측하지 않는다. 참조 번역이 충돌하면 임의로 내용을 합쳐 새 의미를 만들지 않는다.

**항목명·속성형 입력의 용도가 없으면:** 컬럼인지 토글인지 단정하지 않고 중립적인 항목명으로 번역한다. 이미 질문·경고·사건을 표현한 원문은 그 역할을 유지한다. `여부`만 보고 토글 명령, 현재 상태값 또는 확인 질문으로 바꾸지 않는다. 문장형 요청이라도 정확한 의미 보존이 불가능한 내용을 추가해서 문장을 완성하지 않는다.

현재 LocaleGrid 요청은 키, 참조 번역, 대상 언어와 스타일을 전달한다. 컬럼·토글·배지 같은 명시적인 화면 역할은 별도로 전달하지 않으므로, 역할에 따른 완전한 구분에는 한계가 있다.

## 3. 스타일별 작성 규칙

| 스타일 | 목적 | 작성 규칙 |
| --- | --- | --- |
| 자동 | 원문 형태에 맞는 번역 | 단어·구·문장·질문·경고의 기능을 유지한다. 원문이 항목명이면 설명문으로 늘리지 않는다. |
| 라벨형 | 화면에 표시할 이름과 조작 문구 | 컬럼·속성·메뉴는 명사구, 실행 버튼은 동작형으로 표현한다. 설정 컨트롤은 제품 문맥에 맞는 기능명 또는 설정 동작형을 사용하고, 상태 배지는 원문에 주어진 실제 값만 표현한다. |
| 문장형 | 설명·툴팁·확인·경고·로그 | 원문의 의도에 맞는 완결 문장을 우선한다. 명령, 질문, 설명, 발생한 사건을 구분한다. 기술적 의미와 조건을 보존하며 원문에 없는 원인·동작·위험·효과·성공 여부·행위자를 추가하지 않는다. |

### 라벨형의 세부 규칙

- 항목명·속성명·컬럼은 이름을 나타내는 명사구로, 실행 버튼은 구체적인 동작형으로 표현한다.
- 토글·체크박스 등 설정 컨트롤은 동작형을 강제하지 않는다. 제품 규칙에 따라 `Enable Logging` 같은 설정 동작형 또는 `Logging` 같은 기능명을 사용할 수 있다.
- UI 역할이 상태 배지라는 정보만으로 `Enabled`나 `Disabled`를 고르지 않는다. 원문에 실제 활성/비활성 값이 주어진 경우에만 해당 상태값으로 번역한다.

- `Whether`와 `If`를 한국어 `여부`에 대한 고정 번역으로 붙이지 않는다. 조건을 나타내는 실제 의미가 있으면 보존한다.
- `Status`도 모든 항목에 일괄 추가하거나 제거하지 않는다. 이름과 값, 설정과 운영 상태를 구별하는 데 필요한지 판단한다.
- `Enabled`는 boolean 컬럼명으로 쓰일 수도 있고 상태값으로 쓰일 수도 있다. 단어 하나만으로 문장형 위반이라고 판정하지 않고, 확인된 화면 역할과 전체 표현을 본다.
- 용도가 불명확한 `… 여부`를 `… is enabled` 또는 일본어 `…が有効です` 같은 단정문으로 바꾸지 않는다.
- 영어 라벨은 제품의 기존 표기 규칙을 우선한다. 규칙이 없으면 Title Case를 기본으로 하되 관사·짧은 전치사·접속사는 통상적인 소문자 표기를 따른다. 브랜드명과 약어의 고유 표기는 유지한다.
- 일본어 등 다른 언어에는 영어 대소문자 규칙을 적용하지 않는다. 일본어 항목명에 설명형 종결인 `です／ます`를 덧붙이지 않는다.
- 긴 라벨도 허용한다. 기능·대상·조건이 사라지는 축약은 하지 않는다.

### 문장형의 세부 규칙

- 툴팁은 실제로 설명하는 내용만 풀어 쓴다. 입력이 단순 항목명인데 임의로 `Specifies`, `Determines`를 붙여 설정의 기능을 단정하지 않는다. `State`, `Event`, `Fault`, `Alarm`처럼 구분된 기술적 의미도 문장화 과정에서 섞지 않는다.
- 확인 모달은 질문을, 경고는 경고를 유지한다. 일반 설명을 경고로 바꾸거나 실제 경고를 완곡하게 약화하지 않는다.
- 감사 로그는 발생 여부·시제·행위자·대상을 보존한다. 원문에 없는 `successfully`, `admin`, 장비명 등을 추가하지 않는다.
- `Whether the login button is enabled.`는 독립적인 완결 문장 예시로 사용하지 않는다. 문법적으로 문장을 완성하려고 원문에 없는 질문·설정 기능·사실을 덧붙이지 않는다. 용도 정보가 부족한 항목명은 의미 보존을 우선하고 문맥 보완 대상으로 검토한다.
- `정책 적용 실패`는 `The policy failed to apply.`처럼 사건을 문장화할 수 있다. 원문에 없는 `because the network connection was lost`를 추가하면 의미 오류다.


## 4. 네트워크 용어의 의미 구분

아래는 문맥 판단 기준이며 제품 전체에 적용할 고정 치환표가 아니다.

| 의미 | 영어 표현의 기준 | 주의점 |
| --- | --- | --- |
| 관리자가 기능을 켜고 끄는 설정 | Enable / Disable, Enabled / Disabled | 설정 상태를 실제 정상 동작으로 해석하지 않음 |
| 실제 활성 동작·역할 | Active / Inactive 등 제품의 운영 상태 용어 | 링크·연결·동기화 상태까지 Active로 통일하지 않음 |
| 인터페이스·링크 상태 | Up / Down | Administrative State와 Operational State를 구별 |
| 연결·세션 상태 | Connected / Disconnected, Established 등 | 원문의 프로토콜·상태 단계에 맞게 선택 |
| 동기화 상태 | In Sync / Out of Sync 등 | 동기화 기능 활성화와 동기화 완료는 별개 |
| 적용·할당·활성화 | Applied / Assigned / Enabled 등 | NAT 적용을 확인 없이 NAT 활성화로 바꾸지 않음 |
| 허용·거부·폐기·응답 | Allow / Deny / Drop / Reject / Reset 등 | 원문 및 제품 정의에 따라 구별. RST·ICMP·무응답 처리를 추정하지 않음 |

`Deny = RST 전송`, `Block = 무응답 폐기`처럼 제품과 무관한 등식은 사용하지 않는다. Palo Alto Networks의 Deny는 애플리케이션에 따라 기본 거부 동작이 달라질 수 있으며, Junos의 Reject에도 ICMP 응답과 TCP Reset 등 구체적인 선택지가 있다. 따라서 제공된 예시의 동작 설명은 제품 정의가 확인된 경우에만 사용할 수 있다. [Palo Alto Networks 공식 정책 문서](https://docs.paloaltonetworks.com/ngfw/help/10-1/policies/policies-security/building-blocks-in-a-security-policy-rule), [Juniper 공식 필터 문서](https://www.juniper.net/documentation/us/en/software/junos/routing-policy/topics/topic-map/firewall-filter-match-condtions-and-actions-qfx.html)

IP, IPv4, IPv6, MAC, VLAN, VPN, NAT, ACL, DNS, DHCP, BGP, OSPF, TCP, UDP, TLS, HA 등의 약어는 고유 표기를 유지한다. SSL을 TLS로 바꾸거나 제품의 정책 명칭을 다른 벤더의 명칭으로 바꾸는 식의 기술적 교정은 번역 과정에서 임의로 수행하지 않는다.

## 5. 문맥에 따라 달라지는 예시

다음은 지침을 설명하는 예시다. 같은 한국어에 아래 영문을 항상 적용하는 규칙이 아니다.

| 원문 | 확인된 화면 역할 | 가능한 영문 예시 | 판단 기준 |
| --- | --- | --- | --- |
| 인터페이스 사용 여부 | 상세 속성명 | Interface Enablement | 속성의 이름이며 실제 활성 값은 아님 |
| 인터페이스 사용 여부 | 설정 토글 | Enable Interface / Interface | 제품의 동작형/기능명 규칙에 따라 선택 |
| 활성화됨 / 비활성화됨 | 실제 상태 배지 | Enabled / Disabled | 원문에 주어진 활성/비활성 값 사용 |
| 인터페이스 관리 상태 | 설정 상태 컬럼 | Administrative State | 운영 상태와 분리 |
| 링크 상태 | 운영 상태 컬럼 | Link State | 설정 활성화와 구별 |
| 경로 재검색 | 실행 버튼 | Refresh Routes | 단순 이름이 아닌 실행 동작 |
| 로그 기록 여부 | 설정 토글 | Enable Logging / Logging | 동작형 또는 기능명; 제품 표기 규칙에 맞춤 |
| 로그 기록 여부 | boolean 속성 컬럼 | Logging Enabled | 속성이 무엇을 나타내는지 식별 |
| HA 동기화 상태 | 모니터링 컬럼 | HA Sync Status | 동기화 상태를 담는 항목명 |
| 동기화되지 않음 | 상태 배지 | Out of Sync | 실제로 주어진 상태값 |
| 변경 사항을 적용하시겠습니까? | 확인 모달 | Do you want to apply the changes? | 원문의 확인 질문 유지 |
| 규칙 {name}이 비활성화되었습니다. | 감사 로그 | Rule {name} was disabled. | 행위자·성공 여부를 추가하지 않음 |

문제가 제기된 `로그인 버튼 활성화 여부`도 동일하다. 먼저 설정 토글인지, boolean 컬럼인지, 상세 속성명인지 확인해야 한다. 용도 정보 없이 특정 영문 하나를 유일한 정답으로 고정하지 않는다. 표기 차이와 의미 변화는 별도로 평가한다. UI 역할이 불명확한 경우 `Login Button Enablement` 같은 중립적인 항목명을 사용할 수 있다. `Login Button Status`는 제품에서 활성화 상태를 뜻한다는 근거가 있을 때만 사용한다.

`IPv6 인터페이스 자동 연결 사용 여부`를 `Auto Connect`로 줄이면 대상·설정 의미가 사라질 수 있다. 속성명이라면 `IPv6 Interface Auto-Connect Enablement`, 조작형 토글이라면 `Enable IPv6 Interface Auto-Connect`처럼 역할에 맞게 표현할 수 있다. 긴 표현을 허용하며 예시 자체를 정답으로 고정하지 않는다.

## 6. 코드에 반영한 LLM 지침

아래는 구현된 지침을 설명하는 통합본이다. 실제 요청에서는 공통 지침에 선택한 AUTO, LABEL 또는 SENTENCE 지침 하나만 붙이며, 기존 JSON 응답·플레이스홀더 보존 규칙을 유지한다. 실제 문구의 기준은 소스 코드다.

```text
Translate UI text for network management web consoles, including routing,
switching, interfaces, VPN, wireless, monitoring, logging, and network security.
Do not reinterpret ordinary account or UI features as firewall features.

Preserve meaning, polarity, conditions, scope, direction, technical behavior,
placeholders, identifiers, numbers, units, protocol names, and product terms.
Accuracy takes priority over style and brevity. Do not impose a length limit.

Use explicit UI context and source text first. Use the translation key and other
reference translations as supporting evidence, not as permission to invent facts.
Distinguish property names, user actions, configured states, operational states,
status values, help text, confirmations, warnings, and audit events.
If the UI role is unknown and the source names a property, prefer neutral property
wording rather than inventing a toggle action, an observed state, or a confirmation
question. Preserve questions, warnings, and events already expressed by the source.

AUTO: Preserve the source's form and intent in the network UI context. Do not
arbitrarily change the role of a name, action, state, sentence, question, or warning.

LABEL: Translate according to the UI role. Use noun phrases for names, properties,
and columns; action wording for execution buttons; and state values only when
the source supplies the actual state. For setting controls, use clear feature
names or setting actions according to the product convention. Do not force every
toggle label into an imperative. If the UI role is unknown, use neutral property
wording. Do not omit the subject, conditions, negation, or scope, or assert a state
not given by the source. Omit literal Whether/If or redundant Status only when
meaning is preserved. Follow product casing; otherwise use English Title Case
for labels and each target language's own UI conventions. Labels are not summaries
and may be as long as needed.

SENTENCE: Preserve the source's role as help text, a question, warning, or audit
message while translating into natural complete sentences. Preserve technical
meaning and conditions. Do not add causes, behavior, risks, effects, actors, or
success claims absent from the source. A standalone Whether-clause is not a
complete sentence. Accuracy takes priority when completing a fragment would
require inventing facts, a question, or a setting function.

Distinguish configured enablement from actual operation, link state, connection
state, and synchronization. Do not interchange Applied, Assigned, and Enabled.
Do not infer TCP reset, ICMP response, or silent discard from generic Deny/Block.
Preserve vendor-defined actions and terms when they are supplied.

Before returning, check semantic role, technical meaning, selected style,
terminology, casing, and exact preservation of placeholders and identifiers.
Return only the required candidates JSON object. Each candidate has an id (A, B, or C) and a translations object containing every requested locale.
Do not include commentary, alternatives, Markdown fences, or review notes in values.
```

## 7. 품질 확인 기준

- 같은 용어·스타일·문맥에 대해 반복 요청했을 때 기술적 의미와 문법 역할이 유지되는가?
- 설정 토글, boolean 컬럼, 실제 상태 배지의 결과가 문맥에 따라 구분되는가?
- `여부`를 제거하더라도 항목이 상태의 단정으로 바뀌지 않는가?
- 토글의 기능명/동작형 표현을 제품 문맥에 맞게 유지하는가?
- 상태 배지라는 역할만 주어지고 실제 값이 없을 때 Enabled/Disabled를 임의로 선택하지 않는가?
- 문장형이 입력에 없던 원인, 위험, 동작, 효과, 로그 목적지, 행위자, 성공 여부를 추가하지 않는가?
- `Whether …` 절을 완결 문장으로 오인하거나 항목명을 확인 질문으로 바꾸지 않는가?
- 약어·부정·방향·플레이스홀더가 보존되는가?
- 영어 대소문자와 다른 대상 언어의 UI 표현이 일관적인가?
- UI 용도가 없는 입력에서 지나친 추측을 하지 않는가?

최소 반복 사례는 동일 문구의 토글/컬럼/용도 미지정 비교, Enabled/Active/Up/Connected 구분, 적용/할당 구분, 거부 동작의 불명확한 입력, 긴 라벨, 부정문과 플레이스홀더 포함 문구로 구성한다. 표현의 다양성과 의미 오류를 구분해 기록한다. Temperature나 캐시만으로 품질을 보장하지 않는다.


## 8. 구현 및 검증 결과 — 2026-09-07

- 구현: `src/main/java/com/localegrid/llm/TranslationStyle.java`, `TranslationSuggestionService.java`.
- 기존 요청 API, 응답 파싱, 빈 언어 항목 대상 제안, 설정값과 UI 선택 방식은 유지했다.
- 전체 123개 테스트 통과(실패·오류·건너뜀 0). 선택한 모드만 클라이언트에 전달되는지, 공통 네트워크 규칙·JSON·플레이스홀더 계약이 함께 유지되는지 검증했다.
- 로컬 모델 검증은 컴파일된 실제 `TranslationSuggestionService.requestSuggestions`와 기존 HTTP 클라이언트를 호출했다. API 키와 서버 설정은 문서에 저장하지 않았다.
- 모델: `Qwen3.5-9B-6bit`, temperature: `0.2`, 합성 사례 5종·총 9회 요청, 대상 언어 en/ja.
- 명시적인 화면 역할은 새로 추가하지 않았다. 키·참조 번역만으로 역할을 확정할 수 없는 한계가 남아 있다.
- 기존 실행 중인 IDE와 릴리즈 ZIP에는 자동으로 적용되지 않는다. 새 개발 IDE 실행 또는 다음 패키징 때 적용된다.

| 사례 | 반복 | 영어 결과 | 일본어 결과 |
| --- | --- | --- | --- |
| 로그인 버튼 활성화 여부 | 1 | Login button enablement | ログインボタンの有効化 |
| 로그인 버튼 활성화 여부 | 2 | Login button enablement | ログインボタンの有効化 |
| 로그인 버튼 활성화 여부 | 3 | Login button enablement status | ログインボタンの有効化状態 |
| 정책 적용 실패 | 1 | Policy application failed | ポリシー適用に失敗しました |
| 정책 적용 실패 | 2 | Policy application failed | ポリシー適用に失敗しました |
| 정책 적용 실패 | 3 | Policy application failed | ポリシー適用に失敗しました |
| IPv6 인터페이스 자동 연결 사용 여부 | 1 | IPv6 Interface Auto-Connect Setting | IPv6 インターフェース自動接続設定 |
| 규칙 {name}이 비활성화되었습니다. | 1 | Rule {name} has been disabled. | ルール {name} が無効化されました。 |
| 설정을 저장하시겠습니까? | 1 | Save settings? | 設定を保存しますか？ |

### 관찰과 한계

- 로그인 버튼 사례 3회 모두 실제 활성 상태를 단정하는 문장이 아닌 속성명 형태였다. 다만 `Login button enablement`(2회)와 `Login button enablement status`(1회)로 표현이 달랐고, Title Case 지침도 지켜지지 않았다.
- 정책 적용 실패 사례는 영어·일본어 모두 3회 동일했으며, 원문에 없는 원인·행위자를 추가하지 않았다.
- 긴 라벨에서 IPv6·인터페이스·자동 연결·설정 의미를 유지했고, 감사 로그에서 `{name}`이 두 언어 모두 보존됐다. 자동 모드의 질문 형태도 유지됐다.
- 이 검증은 개선 후 결과만 확인한 것이며 이전 프롬프트와의 통제된 비교 실험이 아니다. 전반적인 품질 향상률이나 동일 결과 보장을 주장하지 않는다.
- 모델 응답은 여전히 확률적이다. UI 용도별 정확성, 다른 네트워크 기능·언어, 실제 사내 모델, Windows 환경에 대한 포괄 검증은 하지 않았다.


## 9. 0.17.0 반영 — 2026-09-08

기존 언어 값이 있으면 번역 지침을 사용하고, 모든 값이 비어 있으면 사용자가 설명한 상황을 바탕으로 UI 문구를 작성한다. 작성 요청 자체를 직역하지 않는다. 문구 작성의 자동 모드는 요청한 화면 역할을 판단하고, 라벨형과 문장형은 해당 역할에 맞는 표현 형식을 적용한다.

응답은 `{"candidates":[{"id":"A","translations":{"ko":"저장","en":"Save"}}]}` 형태의 다국어 세트로 받는다. 명확한 안 1개를 우선하며 최대 3개까지 허용한다. 같은 안에는 모든 대상 언어가 있어야 하며, 한 언어의 표현이 같더라도 안 간 연결은 유지한다. 독립적인 언어별 배열을 순서만으로 묶지 않는다.

한국어 [A안]을 적용하면 한국어만 변경되고, 다른 언어에는 [A안] 칩만 남는다. 다른 언어의 값은 사용자가 각각 적용한다. 이 선택 상태는 새 요청에서 초기화한다.

위 8절은 9월 7일 번역 지침 검증 기록이다. 0.17.0의 최종 변경과 검증 범위는 `release/release.md`를 따른다.
