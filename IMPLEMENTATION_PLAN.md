# LocaleGrid 실제 구현 계획

## 목표

`PLAN.md`의 MVP를 실제 동작 가능한 PyCharm 플러그인으로 완성한다.

핵심 목표는 `locales/{locale}/{category}.json` 구조의 JSON 파일을 열었을 때, `JSON` 원본 탭 옆 `다국어 에디터` 탭에서 다국어 값을 비교, 수정, 검증, 저장할 수 있게 만드는 것이다.

## 현재 상태

이미 구성된 기반:

- 기존 `secui-version-checker` 코드는 제거됨
- 플러그인 ID와 이름은 `LocaleGrid`로 변경됨
- `FileEditorProvider` 기반 커스텀 에디터 구조 추가됨
- `locales/{locale}/{category}.json` 경로 인식 로직 추가됨
- dot path flatten/unflatten 기본 로직 추가됨
- locale별 key union 병합 로직 추가됨
- Swing 기반 그리드 UI 초안 추가됨
- 프로젝트 설정 UI 초안 추가됨

현재 구현 완료 및 보완된 부분:

- **구현 언어**: MVP 안정성 확보를 위해 기존 Java 구조를 유지하며 최적화 완료
- **저장 전 변경 요약**: `SaveResult`를 통해 추가, 수정, 삭제, 생성된 파일 수 등의 명확한 변경 요약을 상태 바와 정보 메시지로 제공
- **Key 편집 및 Rename 추적**: `LocaleGridRow`에 `added` 필드 및 `originalKey`를 도입하여 key 이름 변경 시 `편집` 상태로 관리하고 중복 검증 연동 완료
- **한글화 및 UI 개선**: 상태 배지를 한글(`추가`, `경고`, `편집`, `삭제`, `에러`)로 직관적으로 변경하고, 동일한 기준으로 필터 토글 버튼 연동
- **예외키 보존**: root-level `__section__` entry를 hidden marker로 보존하고, 새로 추가한 예외키 row는 상태 배지 없이 편집 가능하도록 구현
- **대화상자 레이아웃 개선**: `WideConfirmDialog`를 도입하여 에디터 종료 및 취소 시 예쁘고 넓은 경고창 제공
- **단위 테스트**: `FlattenedJsonTest`에 중복 key 검증 및 dot path 구조 분석에 대한 단위 테스트 추가 완료
- **locale별 예상 문자 체계 검사**: ko/en/ja/vi 고정 내장 규칙, CLDR likely-subtags, Java 정규표현식 + ICU fallback, BCP 47 정규화, 셀 단위 진단을 추가하고 기본 `경고 (저장 가능)` 또는 선택 가능한 `에러 (저장 차단)` 정책으로 구성
- **설정 UI 고도화**: 사용자 친화성과 가독성을 극대화하기 위해 플러그인 설정(Settings) 화면을 전면 개편 완료
  - **기본/고급 설정 영역 분리**: 설정 항목들을 '기본 설정'과 '고급 설정' 영역으로 명확히 구획하고, 고급 설정은 펼치고 접을 수 있는 IntelliJ 스타일의 `HideableTitledPanel`로 묶어 복잡성 완화
  - **상세 힌트 설명 및 개행**: 각 입력 필드 아래에 폰트 크기와 색상을 조정한 보조 힌트 라벨을 배치하고, 긴 텍스트에는 HTML 형식의 개행(`<br>`)을 적용하여 폼 가독성 극대화
  - **플레이스홀더(Placeholder) 제공**: `locale 표시 순서` 필드에 `JBTextField`의 Empty Text API를 적용해 placeholder(`ko,en,ja,vi`)를 노출하고 힌트 텍스트에도 괄호 예시 추가
  - **명칭 표준화**: 기존 '구역 표시 key' 명칭을 **예외 키**로 용어를 일원화 및 설명 문구 교체
  - **JSON 들여쓰기 컴포넌트 교체**: 기존의 숫자 Spinner를 들여쓰기 2칸 혹은 4칸을 고를 수 있는 **JComboBox(SelectBox)** 형태의 콤보박스로 변경 및 연동 완료
- **기어 아이콘 버튼 외관 개선**: 다국어 에디터의 기어(설정) 버튼이 테두리와 마우스 리액션이 없어 비활성화된 것처럼 보였던 현상을 개선. 기존 `MoveActionButton` 디자인 사양을 계승한 `ToolbarIconButton`을 구현하고 설정 기어 버튼 및 위/아래 이동 버튼에 통합 적용하여 둥근 테두리와 호버/클릭 피드백을 제공함. 아울러 기어 아이콘 자체를 선명한 흰색(`java.awt.Color.WHITE`)으로 틴트 적용하여 다크 테마(Darcula)에서의 시인성을 높이고 흐려 보이는 비주얼을 완벽히 해결함.
- **상단 툴바 레이아웃 및 작업 버튼 개선**: 다국어 에디터 상단 툴바를 필터, 검색, 설정의 3분할 구조로 구성했습니다. 상태 필터 버튼 크기를 줄이고, `추가`, `편집`, `삭제`, `삭제 취소`, `예외키`, `설정` 버튼에 의미에 맞는 아이콘과 라벨을 함께 표시합니다. 텍스트 작업 버튼과 행 이동 버튼의 외곽선을 제거하고 `예외키`, `설정` 버튼 폭을 간결하게 조정했습니다.
- **검색창 UI 개선**: 기존의 텍스트 라벨 "검색"과 결합된 형태에서 라벨을 완전히 배제하고, 입력창 내부에 돋보기 검색 아이콘 및 입력 지우기(X) 버튼이 깔끔하게 내장된 `JBTextField`로 전환하였습니다. 또한 둥근 모서리(RoundRect) 속성을 활성화하여 부드러운 느낌을 부여하고, 가로 폭을 기존 320px에서 420px로 늘려 검색 영역을 더 확장하였으며, placeholder 텍스트를 "검색"으로 지정하여 직관적이고 세련된 미니멀 검색 영역을 완성했습니다.
- **검색 탐색 및 선택형 필터**: 검색 기본 동작을 전체 Row 유지와 셀 텍스트 하이라이트로 변경하고, 매칭 Row 기준 `현재 / 전체` 건수와 순환형 이전·다음 탐색을 추가했습니다. 검색창의 필터 아이콘을 활성화한 경우에만 검색 결과 Row로 테이블을 제한하며, 상태 필터 결과 안에서 검색 건수와 탐색 대상을 계산합니다.
- **검색·상세 편집 debounce**: 검색 입력은 500ms, 상세 value 편집 후 전체 검증과 테이블 갱신은 300ms debounce로 묶었습니다. value 자체는 즉시 모델에 반영하고 적용·필터 조작·포커스 이동 전에는 대기 중 갱신을 즉시 처리해 입력 유실 없이 연속 입력 성능을 개선했습니다.
- **드래그 핸들러 디자인 개선**: 행 순서 변경용 드래그 핸들(6도트 아이콘)을 입체적이고 현대적인 느낌으로 업그레이드하였습니다. 도트 크기를 4px로 조절하고, 개별 도트마다 3D 입체 엠보싱 효과(Drop shadow 그림자 및 Highlight 오버레이)를 적용하여 뛰어난 시각적 완성도를 구현했습니다. 또한 다크 테마 시인성을 위해 활성화 도트 색상의 명도를 높였습니다.
- **용어 통일 및 0.2.0 릴리즈**: 기존에 사용되던 모호한 용어인 '번역 묶음'을 직관적인 행동형 명칭인 **'일괄보기'**로 테이블 컬럼 헤더 및 상단 툴바 체크박스 모두 일괄 갱신했습니다. 또한 플러그인 버전을 `0.2.0`으로 공식 상향 조정한 뒤 빌드를 수행하여 배포용 릴리즈 zip 파일을 성공적으로 완성했습니다.

## 결정 필요 사항

### 1. 구현 언어

PLAN에는 Kotlin으로 구현한다고 되어 있지만 현재 코드는 Java로 시작되어 있다.

권장 결정:

- MVP 안정화까지는 현재 Java 구조를 유지한다.
- 기능이 고정된 뒤 필요하면 Kotlin으로 점진 이전한다.

이유:

- 이미 에디터, 모델, 로드/저장 구조가 Java로 만들어져 있다.
- 지금 Kotlin 전환을 먼저 하면 기능 검증보다 구조 변경 비용이 커진다.
- JetBrains Platform API는 Java/Kotlin 모두 사용 가능하다.

### 2. 다국어 표시 방식

한국어, 영어, 일본어, 베트남어를 모두 한 줄에 컬럼으로 표시하면 다음 문제가 생긴다.

- 한 행이 지나치게 가로로 길어진다.
- 언어별 문장 길이가 달라 셀 높이와 가독성이 흔들린다.
- CJK, 라틴, 베트남어 악센트가 섞이면 한 줄 비교가 피로해진다.

권장 결정:

- MVP의 기본 화면은 `key + 일괄보기` 중심으로 표시한다.
- `일괄보기` 컬럼에는 한 셀 안에 `ko:`, `en:`, `ja:`, `vi:`처럼 locale별 값을 줄바꿈으로 요약 표시한다.
- 언어별 개별 컬럼도 제공하되, 테이블 위 체크박스로 `일괄보기` 컬럼과 각 언어 컬럼을 show/hide 할 수 있게 한다.
- 기본 표시 컬럼은 `key + 언어별 컬럼`이다.
- `일괄보기` 컬럼은 마지막 컬럼으로 배치하고 기본값은 숨김이다.
- 상태는 key 왼쪽의 별도 고정폭 `상태` 컬럼에 배지로 표시한다.
- 문제 없는 행은 상태 배지를 표시하지 않는다.
- 자동 탐지 locale 표시 순서는 `ko`, `en`, `ja`, `vi`를 우선하고, 나머지는 이름순으로 뒤에 붙인다.
- 그리드 셀에서는 긴 텍스트를 직접 편집하지 않고, 선택 행 상세 패널에서 locale별 값을 바로 수정한다.
- 이후 필요하면 표시할 locale을 선택하거나 접는 옵션을 추가한다.

즉, 기본 UI는 다음 구조를 권장한다.

```text
상단: toolbar, search, filter, column visibility checkboxes

중앙 그리드:
상태   key                  ko          en       ja          vi          일괄보기(hidden)
경고   login.title          로그인      Login    ログイン    Đăng nhập  ko: 로그인 ...
       login.button.submit  로그인하기  Sign in  ログイン하는 Đăng nhập  ko: 로그인하기 ...

하단 또는 우측 상세 패널:
ko  로그인하기
en  Sign in
ja  ログインする
vi  Đăng nhập
```

이 방식은 PLAN의 key union 그리드 구조를 유지하면서도, 다국어 장문을 가로 컬럼에 모두 욱여넣는 문제를 피한다.
또한 사용자는 필요에 따라 `일괄보기`만 보거나, 특정 언어별 컬럼만 켜서 비교할 수 있다.
실제 편집은 상세 패널에서 안정적으로 수행한다.

### 3. locale별 예상 문자 체계 검사

권장 결정:

- 문자열의 실제 언어를 추정하지 않고 Unicode Script 기준으로 예상하지 않은 문자를 검사한다.
- ko/en/ja/vi는 CLDR 결과보다 우선하는 확정 내장 규칙으로 제공한다.
- locale 태그의 대소문자와 `_`/`-`를 정규화해 BCP 47 형식으로 해석하고, 명시된 Script subtag는 존중한다.
- 그 외 올바른 BCP 47 locale은 명시 Script가 없을 때 CLDR likely-subtags가 예상 native Script를 제공하면 규칙을 자동 생성한다.
- 고정·자동 생성 규칙은 결정된 Script와 라틴(Latin), Common에 속하는 공백·문장부호·기호, Inherited에 속하는 결합 문자를 허용한다.
- 모든 Unicode 숫자 범주를 허용하고, 런타임 Unicode 버전보다 새로운 보조 평면 이모지를 위해 U+1F000–U+1FAFF 범위만 제한적 fallback으로 허용한다.
- CLDR에서 Script를 구할 수 없거나 malformed locale이면 검사하지 않는다.
- 허용 Script 목록에서 위반 문자 탐지 Java 정규표현식을 자동 생성해 빠르게 1차 탐지하고 같은 규칙의 Pattern을 재사용한다.
- Java 17이 잠재 위반 코드포인트를 `UNKNOWN`으로 분류할 때만 IDE 번들 ICU `UScript`/`UCharacter`로 허용 Script와 숫자 여부를 교차 확인해, Toto/Nagm/Kawi/Vith와 신규 Arabic/Latin/Han 문자·숫자의 오탐을 방지한다.
- `문자 체계 검사`는 기본으로 켜고 프로젝트 설정에서 끌 수 있게 한다.
- 위반은 기본 `경고 (저장 가능)`로 표시하며, 프로젝트 설정에서 `에러 (저장 차단)`로 변경할 수 있다.

확정 내장 규칙:

| locale | 대상 문자 체계 |
| --- | --- |
| `ko` | 한글(Hangul) |
| `en` | 라틴(Latin) |
| `ja` | 히라가나(Hiragana), 가타카나(Katakana), 한자(Han) |
| `vi` | 라틴(Latin) |

CLDR는 locale을 예상 Script에 매핑하는 용도로만 사용하며 value의 자연어를 판별하지 않는다. 모든 고정·자동 생성 규칙은 라틴(Latin)을 허용하므로 베트남어·프랑스어처럼 라틴 기반인 문장은 ko/ja 값에서도 통과할 수 있고, 일본어와 중국어의 한자도 Han만으로 구분할 수 없다. 따라서 기능명과 진단 문구는 `잘못된 언어 감지`가 아니라 `예상 문자 체계 검사`와 `허용되지 않은 문자`를 사용한다.

## 구현 단계

## 1단계: 모델 정리

목표:

- UI 동작보다 먼저 저장과 검증에 필요한 내부 모델을 확정한다.

작업:

- `TranslationTable`에 기준 파일, category, locale 순서, row 순서 정보를 명확히 저장
- `LocaleGridRow`에 상태값 추가
  - `normal`
  - `missing`
  - `modified`
  - `deleted`
  - `warning`
  - `error`
- rename 추적 모델 추가
  - `originalKey`
  - `currentKey`
  - `renamed`
- locale별 파일 존재 여부 모델 추가
- 예외키 row 여부와 hidden marker 위치를 row/table 레벨에 명확히 저장

완료 기준:

- 행 하나만 보고도 추가, 수정, rename, 삭제, warning, error 여부를 판단할 수 있다.

## 2단계: JSON 처리 로직 고도화

목표:

- 저장 시 원본 구조를 최대한 보존하고, dot path 충돌을 안정적으로 막는다.

작업:

- flatten 시 key 순서 보존 강화
- unflatten 시 leaf/object 충돌 검증 강화
- root-level 예외키는 dot path 변환 대상에서 제외하고 hidden marker로 보존
- unsupported value 타입 표시용 metadata 추가
- 저장 전 모든 locale JSON을 메모리에서 먼저 생성
- JSON 직렬화 실패 시 실제 파일 쓰기 차단

완료 기준:

- 저장 전에 모든 대상 JSON 문자열이 생성되고 검증된다.
- error가 있으면 어떤 파일도 쓰지 않는다.

## 3단계: 검증 체계 정리

목표:

- 저장 차단 error와 허용 warning을 명확히 분리한다.

error:

- 빈 key
- 잘못된 dot path
- 중복 일반 번역 key
- leaf/object dot path 충돌
- JSON 파싱 실패
- JSON 직렬화 실패
- 문자 위반 처리가 error일 때 locale별 예상 문자 체계 위반

warning:

- 빈 value
- 누락 locale 파일
- unsupported readonly value
- 일부 locale에만 존재하는 key
- locale별 예상 문자 체계 위반(기본 warning 설정)

작업:

- 기존 `Diagnostic`의 key 기반 진단에 `locale` 필드를 추가해 row/locale 셀과 연결하고, `file`/`column` 정보 확장은 후속 단계로 둠
- locale 코드의 대소문자와 `-`/`_` 구분자를 정규화해 BCP 47 형식으로 파싱하고 명시 Script subtag를 존중
- ko/en/ja/vi의 대상 Script를 CLDR보다 우선하는 고정 내장 규칙으로 정의
- 그 외 올바른 locale은 명시 Script가 없을 때 CLDR likely-subtags에서 native Script를 구해 Latin, Common, Inherited와 함께 자동 규칙 생성
- Unicode 숫자 범주 전체와 U+1F000–U+1FAFF의 제한적 이모지 호환 fallback을 공통 허용 범위에 포함
- 허용 Script 목록에서 Java 정규표현식을 자동 생성해 빠르게 1차 탐지하고 Pattern을 재사용
- Java 17에서 Script가 `UNKNOWN`인 잠재 위반만 IDE 번들 ICU `UScript`/`UCharacter`로 허용 Script·숫자 여부를 재확인한 뒤, 실제 위반 문자와 해당 locale 셀 정보를 진단에 포함
- CLDR에서 Script를 구할 수 없는 locale 또는 malformed locale은 진단을 생성하지 않고 건너뜀
- 프로젝트 설정의 검사 사용 여부와 warning/error 처리 수준을 진단 severity에 반영
- Validate 버튼 결과를 상태 영역과 팝업에서 모두 확인 가능하게 정리
- Save 실행 시 error가 있으면 저장 차단
- warning만 있으면 사용자 확인 후 저장 가능

완료 기준:

- 사용자가 왜 저장이 막혔는지 row와 locale 단위로 확인할 수 있다.
- 사용자가 문자 체계 위반 위치를 셀 단위로 확인할 수 있다.
- 기본 warning에서는 확인 후 저장할 수 있고, error 설정에서는 위반 문자가 남아 있으면 저장이 차단된다.

## 4단계: 그리드 UI 개선

목표:

- 다국어가 섞여도 읽기 쉬운 편집 화면을 만든다.

작업:

- 기본 그리드는 `상태`, `key`, 언어별 컬럼, `일괄보기`로 구성
- `일괄보기` 셀은 locale별 값을 줄바꿈 preview로 표시
- 테이블 위에 컬럼 show/hide 체크박스 제공
  - `일괄보기`
  - `ko`
  - `en`
  - `ja`
  - `vi`
- `일괄보기`은 마지막 컬럼이며 기본 hide
- 언어별 컬럼은 기본 show
- key 왼쪽의 별도 `상태` 컬럼에 상태 배지 표시
  - 배지는 고정폭 텍스트로 표시
  - 예: `추가`, `경고`, `편집`, `삭제`, `에러`
  - 정상 행은 배지를 표시하지 않음
- locale 표시/숨김 또는 preview locale 수 제한 옵션 추가
- 기준 locale과 대상 locale 선택 UI는 상세 패널 편집 보조 기능으로 추가
- 선택 행 상세 패널 추가
  - 모든 locale value를 세로로 표시
  - 문자열 value는 편집 가능
  - readonly value는 타입과 원본값 표시
- key 컬럼 고정 또는 넓은 기본 폭 적용
- 긴 value는 `일괄보기`에서 줄 단위 축약, 상세 패널에서 전체 편집
- 색상 규칙 정리
  - error row
  - warning row
  - deleted row
  - exception key row
  - modified cell

완료 기준:

- ko/en/ja/vi가 모두 있어도 사용자가 필요한 컬럼만 켜서 볼 수 있다.
- 선택 행에서 모든 locale 값을 한 번에 확인하고 수정할 수 있다.

## 5단계: key 편집 기능 완성

목표:

- Add, Rename, Delete 흐름을 PLAN에 맞게 완성한다.

작업:

- Add Row 팝업
  - 새 dot path key 입력
  - 선택 행 아래 삽입
  - 선택 행이 없으면 끝에 삽입
  - 예외키 입력 시 visible 예외키 row로 처리
- Rename Key 팝업
  - 기존 key 표시
  - 새 key 입력
  - 영향받는 locale 파일 목록 표시
  - 충돌 검증 결과 표시
  - rename 추적 저장
- Delete Row
  - 즉시 삭제가 아니라 삭제 후보 표시
  - Save 전 삭제 대상 목록 표시
- Undo 수준은 MVP에서 제외

완료 기준:

- Save 전 변경 요약에서 추가, 수정, rename, 삭제 수가 구분된다.

## 6단계: 저장 흐름 완성

목표:

- 저장 전 검증, 요약, 확인, 파일 쓰기 순서를 안정화한다.

저장 순서:

1. 현재 셀 편집 종료
2. Validate 실행
3. error가 있으면 저장 차단
4. warning 목록 생성
5. 변경 요약 생성
6. 누락 locale 파일 생성 여부 확인
7. 모든 JSON을 메모리에서 생성
8. 대상 파일에 쓰기
9. VFS refresh
10. 테이블 reload

변경 요약 항목:

- 추가 key 수
- 수정 key 수
- rename key 수
- 삭제 key 수
- 생성될 locale 파일 목록
- 저장될 locale 파일 목록
- warning 수
- 저장 차단 error 여부

완료 기준:

- 저장 전에 사용자에게 실제 변경 범위가 명확히 보인다.

## 7단계: 프로젝트 설정 완성 및 UI 개선

목표:

- 프로젝트별 locale grid 설정을 안정적으로 저장하고, 사용자 친화적이고 직관적인 설정 UI로 고도화한다.

작업:

- **설정 화면 레이아웃 고도화**:
  - 설정 화면을 '기본 설정'과 '고급 설정' 영역으로 명확히 분리하여 그룹화.
  - '기본 설정': locales root 경로 설정, locale 표시 순서 설정과 각 필드 하단의 상세 안내(힌트) 텍스트 추가.
  - '고급 설정': 접기/펼치기가 가능한 IntelliJ 스타일 접이식 패널(HideableTitledPanel)로 구현.
- **예외 키 설정 개선**:
  - 기존 '구역 표시 key'의 라벨명을 '예외 키'로 변경하고, 설명 문구를 "번역 key가 아니라 예외 키로 보존할 key입니다. 쉼표로 구분합니다."로 수정.
- **JSON 들여쓰기 설정 개선**:
  - 기존의 숫자 Spinner 대신, 2와 4 중 선택할 수 있는 콤보박스(SelectBox) 형태의 JComboBox UI 컴포넌트로 변경.
  - 하단 설명 문구: "적용 시 저장되는 JSON 들여쓰기 칸 수입니다. 기본값은 2입니다."
- **예상 문자 체계 검사 설정 추가**:
  - `문자 체계 검사`의 `검사 사용` 체크박스를 제공하고 기본값은 켬으로 저장.
  - `문자 위반 처리` 콤보박스에서 `경고 (저장 가능)`와 `에러 (저장 차단)`를 선택하며 기본값은 경고.
- **설정 반영 동기화**:
  - locales root, locale 표시 순서, 예외키를 구조 설정으로 분류하고, 열린 LocaleGrid 에디터에 미저장 변경이 하나라도 있으면 구조 설정 적용을 차단.
  - 구조 설정 적용 성공 시 프로젝트 message bus로 모든 열린 LocaleGrid 에디터를 새 설정으로 재로드.
  - 재로드 실패 시 이전 stale table을 제거하고 로드 실패 상태를 표시.
  - JSON 들여쓰기와 문자 체계 검사 같은 비구조 설정은 테이블을 유지한 채 진단을 다시 계산.

완료 기준:

- 설정 화면에서 2와 4 중 들여쓰기를 선택할 수 있고, 예외 키와 예상 문자 체계 검사 설정이 프로젝트 XML에 정확하게 로드/세이브 및 반영된다.
- 미저장 에디터 보호, message bus 기반 전체 재로드, 재로드 실패 시 stale table 제거가 동작한다.


## 8단계: 테스트 추가

목표:

- 핵심 로직은 UI 없이 단위 테스트로 검증한다.

단위 테스트:

- JSON flatten
- JSON unflatten
- dot path 문법 검증
- dot path 충돌 검증
- key union 병합
- 기준 파일 key 순서 보존
- locale 자동 탐지
- locale 변형 정규화
- ko/en/ja/vi 예상 문자 체계 허용/위반 판정
- 명시 Script subtag 존중과 CLDR likely-subtags 파생 규칙 판정
- Unicode 숫자 전체와 제한적 이모지 fallback 허용
- Java 정규표현식 1차 탐지와 Java 17 `UNKNOWN` 코드포인트의 ICU Script/숫자 fallback
- CLDR Script 미확정 또는 malformed locale 검사 생략
- category 추론
- 예외키 판별
- add/rename/delete 반영
- 저장용 JSON 생성

수동 테스트:

- `locales/ko/login.json` 열기
- `JSON` / `다국어 에디터` 탭 표시 확인
- ko/en/ja/vi 파일 병합 확인
- ko 값의 일본어 문자, en/vi 값의 비라틴 문자, ja 값의 한글 문자가 warning으로 표시되는지 확인
- 지역 locale 변형에는 고정 내장 규칙이 적용되고 명시 Script subtag는 존중되는지 확인
- ru/ar/hi 등 CLDR에서 native Script를 제공하는 locale도 검사되는지 확인
- Toto/Nagm/Kawi/Vith와 신규 Arabic/Latin/Han 문자·숫자가 ICU fallback으로 오탐 없이 통과하고 허용하지 않은 신규 Script는 진단되는지 확인
- 기본 warning 설정에서 문자 체계 위반이 있어도 저장 가능한지 확인
- error 설정에서 문자 체계 위반이 남아 있으면 저장이 차단되는지 확인
- 검사를 끄면 문자 체계 진단이 생성되지 않는지 확인
- CLDR에서 Script를 구할 수 없는 locale 또는 malformed locale은 검사를 건너뛰는지 확인
- 미저장 LocaleGrid 에디터가 있으면 구조 설정 적용이 차단되는지 확인
- 구조 설정 적용 시 열린 에디터가 모두 재로드되고, 실패한 에디터의 이전 테이블이 제거되는지 확인
- 긴 다국어 value 표시 확인
- 상세 패널 편집 확인
- Add Row 확인
- Rename Key 확인
- Delete Row 확인
- Save 요약 확인
- 누락 locale 파일 생성 확인

완료 기준:

- `.\gradlew.bat build`가 통과한다.
- `runIde` 샌드박스에서 수동 체크리스트를 통과한다.

## 9단계: AI 다국어 번역 제안 (사내 호스팅 LLM 연동)

목표:

- 사내 호스팅 `qwen3.6-27b`를 비롯한 OpenAI 규격 호환 REST LLM을 연동하여, 하단 상세 패널에서 원클릭으로 고품질 다국어 번역 추천을 제공한다.

세부 구현 내용:

- **설정 모델 및 UI 확장 (`LocaleGridSettingsState`, `LocaleGridSettingsConfigurable`)**:
  - LLM 활성화 여부(`llmEnabled`), 엔드포인트 URL(`llmEndpoint`), 모델 식별자(`llmModel`), API Key(`llmApiKey`), 타임아웃(`llmTimeoutSeconds`) 설정 추가.
  - 접이식 패널 `사내 AI 번역 제안 (LLM 연동)` 제공 및 실시간 엔드포인트 응답 속도를 측정하는 `[연결 테스트]` 버튼 지원.
  - 사용자 지침에 따라 팁 상자 배제 및 깔끔한 보조 힌트 텍스트 배치.
- **LLM 비동기 통신 클라이언트 (`LocaleGridLlmClient`)**:
  - Java 17 표준 `java.net.http.HttpClient`와 `org.json`을 사용하여 외부 종속성 없이 비동기 통신 구현.
  - Markdown 코드 블록(```` ```json ````) 자동 정제 및 JSON 본문 추출 로직(`extractJsonBlock`) 지원.
- **다중 언어 문맥 기반 추천 서비스 (`TranslationSuggestionService`)**:
  - 하단 패널에 입력된 모든 언어의 텍스트를 참조(Reference Context)로 취합하여 LLM에 전달, 번역의 다의성을 해소하고 어순 최적화.
  - 시스템 프롬프트에 플레이스홀더(`{0}`, `{name}`, `%s`, HTML 태그) 원본 보존 규칙 강제.
  - 응답 JSON 파싱 및 플레이스홀더 누락 검증 로직 구현.
- **추천 칩 UI 컴포넌트 (`TranslationSuggestionChip`)**:
  - 앞쪽 별 아이콘·추천 문구·닫기 버튼으로 구성한 저채도 보라색 칩. `AI 제안` 문구는 표시하지 않는다. 글꼴 11pt, 상하 여백 4px, 닫기 버튼 20px로 한 줄 높이 약 28px를 유지한다. 고정 최대 너비 없이 내용에 딱 맞추되, 한 줄의 가용 너비를 넘으면 `...`로 말줄임하고 HTML 이스케이프한 툴팁으로 전체를 보여준다.
  - 칩 클릭 또는 Enter/Space로 원문 전체를 적용한다. 입력란 높이·세로 스크롤은 유지한다. 키보드 포커스와 접근성 이름을 제공한다.
- **다국어 에디터 연동 (`LocaleGridFileEditor`)**:
  - 하단 상세 패널 키명 옆에 단색 별 아이콘과 은은한 보라색 테두리의 `[AI 번역 제안]` 버튼 배치. 비활성 상태는 흐린 색으로 구분한다.
  - 참조 문장과 편집 가능한 빈 언어 항목이 모두 있을 때만 활성화한다. 모든 언어가 입력되면 비활성화하며, 요청 대상도 빈 언어 항목으로 제한한다. 입력 및 추천 적용 직후 상태를 갱신한다.
  - 버튼 클릭 시 `번역 생성 중…` 로딩 상태 전환 및 비동기 처리.
  - 응답 완료 시 각 대상 언어 에디터 상단에 추천 칩 노출.
  - 칩 또는 번역 문구 클릭 시 `editor.setText()` 호출 → 기존 `DocumentListener`가 트리거되어 테이블 상태가 일반 **'편집'** 상태로 자연스럽게 전환 (별도 AI 수식어 배제). 닫기 버튼은 적용하지 않고 제안만 닫는다.
  - Row 선택 전환 시 이전 추천 칩 자동 정리.
  - 하단의 기존 상태 정보는 AI 안내로 덮어쓰지 않는다. 진행·결과·실패 안내는 `AI 번역 제안` 버튼 바로 오른쪽의 별도 라벨에 표시하며, 공간이 부족하면 AI 안내만 말줄임하고 툴팁을 제공한다.

완료 기준:

- 설정에서 사내 LLM 엔드포인트 및 모델(`qwen3.6-27b` 등)을 등록하고 연결 테스트에 성공한다.
- 하단 패널에서 키명 옆 버튼을 클릭하면 빈 언어 항목에 추천 칩이 표시된다.
- 칩 또는 번역 문구를 클릭하면 텍스트가 적용되고 테이블 행이 표준 '편집' 상태로 전환된다.
- 핵심 LLM 통신, 프롬프트 생성, JSON 파싱 및 칩 컴포넌트 단위 테스트가 모두 통과한다.

## 우선순위

1. 모델과 저장 안정성
2. 검증 정확도
3. 다국어 표시 UI 개선
4. key 편집 UX 완성
5. AI 다국어 제안 기능 완성
6. 테스트 추가
7. README와 PLAN 반영

## 권장 구현 순서

1. Java 유지 결정 후 현재 코드 안정화
2. `TranslationTable`, `LocaleGridRow`, `LocaleValue` 모델 보강
3. `Diagnostic` 구조 확장과 locale별 예상 문자 체계 검사 추가
4. 저장 전 메모리 검증 강화
5. 그리드 UI를 `상태 배지 컬럼 + key + 언어별 컬럼 + 일괄보기 마지막 컬럼 기본 hide + 상세 패널` 구조로 개선
6. Add/Rename/Delete 저장 반영 완성
7. 사내 AI 번역 제안 (LLM 연동) 구현 및 칩 UI 완성
8. 단위 테스트 추가
9. 샌드박스 프로젝트 수동 검증

## 최종 MVP 완료 기준

- JSON 파일을 열면 `JSON` / `다국어 에디터` 탭이 표시된다.
- 같은 category의 locale JSON 파일들이 하나의 테이블로 병합된다.
- 다국어 value를 한 화면에서 비교하고 수정할 수 있다.
- 긴 다국어 문장이 한 줄에 과밀하게 표시되지 않는다.
- Add, Rename, Delete, Save, Refresh, Validate가 동작한다.
- 빈 value는 warning으로 표시되고 저장은 가능하다.
- ko/en/ja/vi의 예상 문자 체계 위반은 기본적으로 locale 셀 warning으로 표시되고 저장은 가능하다.
- 그 외 올바른 BCP 47 locale은 CLDR likely-subtags에서 예상 Script를 구할 수 있을 때 자동 검사된다.
- 프로젝트 설정에서 문자 위반 처리를 error로 바꾸면 위반 문자가 남아 있는 동안 저장이 차단된다.
- 문자 체계 검사를 프로젝트별로 켜거나 끌 수 있다.
- 사내 호스팅 LLM(Qwen 등) 및 범용 모델을 활용한 다국어 번역 제안 칩 기능이 지원된다.
- 지역 locale 변형에는 고정 규칙이 적용되고 명시 Script는 존중되며, CLDR에서 Script를 구할 수 없거나 malformed locale이면 검사를 건너뛴다.
- 구조 설정은 미저장 에디터를 보호하며, 적용 성공 시 열린 에디터에 동기화되고 재로드 실패 시 stale table을 남기지 않는다.
- 중복 key와 dot path 충돌은 error로 표시되고 저장이 차단된다.
- 누락 locale 파일은 저장 시 사용자 확인 후 생성된다.
- 저장 전 변경 요약이 표시된다.
- 핵심 JSON 처리 로직 및 AI 제안 단위 테스트가 통과한다.
