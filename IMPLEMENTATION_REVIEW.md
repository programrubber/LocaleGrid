# LocaleGrid 구현 기능 점검

점검일: 2026-05-27

## 요약

현재 구현은 `locales/{locale}/{category}.json` 또는 `locales/{locale}/{category}_{locale}.json` 구조의 JSON 파일을 IDE 안에서 다국어 에디터로 묶어 보고, 추가/편집/삭제/순서 변경 후 적용하는 흐름까지 포함한다.

핵심 기능은 대부분 구현되어 있으며, 특히 예외키 보존, JSON 순서 보존, 드래그 기반 순서 변경, 적용 전 확인 팝업, 설정 화면, 상태 표시까지 동작하도록 구성되어 있다.

## 구현된 주요 기능

### 파일 인식 및 에디터 탭

- locale JSON 판별은 `LocaleGridPath`에서 처리한다.
- 지원 구조:
  - `locales/{locale}/{category}.json`
  - `locales/{locale}/{category}_{locale}.json`
- JSON 파일을 열면 기본 JSON 원본 탭을 `JSON`으로 감싸고, 그 뒤에 `다국어 에디터` 탭을 추가한다.
- 대상 파일이 locale JSON 구조가 아니면 LocaleGrid 에디터는 열리지 않는다.

### 로드 및 병합

- `TranslationTableLoader`가 같은 category의 locale 파일을 locale별로 읽어 하나의 `TranslationTable`로 구성한다.
- locale 순서는 수동 설정이 있으면 설정값을 우선하고, 없으면 locale 루트 하위 디렉터리를 자동 감지한다.
- 자동 감지 시 `ko`, `en`, `ja`, `vi`를 우선 배치하고 나머지는 이름순으로 배치한다.
- 열린 파일의 locale key 순서를 먼저 반영한 뒤, 다른 locale의 추가 key를 뒤에 병합한다.
- 존재하지 않는 locale 파일은 warning diagnostic으로 기록한다.

### JSON 파싱 및 저장

- `FlattenedJson`은 JSON 객체를 dot path key로 펼친다.
- root-level 중복 JSON key와 dot path 중복은 error diagnostic으로 기록한다.
- 문자열 값은 편집 가능하고, number/boolean/array/object 등은 readonly로 표시한다.
- `JsonTreeWriter`는 dot path를 다시 nested JSON으로 구성하고, root entry list를 통해 root-level 중복 entry도 출력할 수 있다.
- `TranslationTableSaver`는 적용 시 locale별 JSON을 재구성하고 IntelliJ write command 안에서 파일을 갱신한다.
- 누락 locale 파일은 값이 있고 사용자가 생성을 선택한 경우에만 생성한다.

### 예외키

- 설정 필드는 `exceptionKeys`이며 기본값은 `__section__`이다.
- 예외키는 root-level key와 정확히 일치할 때만 예외키로 처리한다.
- 원본 JSON에서 읽은 root-level 예외키는 테이블에 표시하지 않고 hidden marker로 보존한다.
- hidden marker는 가까운 non-exception root entry 앞/뒤 anchor를 기준으로 저장 위치를 유지한다.
- 테이블에서 새로 추가하거나 편집한 key가 예외키와 일치하면 visible 예외키 row로 처리한다.
- visible 예외키 row는 상태 배지를 표시하지 않고, 중복/dot path/빈 값/unsupported value 검증에서 제외된다.
- 저장 시 hidden marker와 visible 예외키 row는 root-level entry로 출력된다.

### 다국어 에디터 UI

- 그리드 컬럼 순서는 `핸들 | 상태 | key | locale... | 일괄보기` 구조다.
- locale 컬럼은 체크박스로 show/hide 가능하다.
- `일괄보기` 컬럼은 모든 locale 값을 한 셀에서 줄 단위로 보여준다.
- 선택 row의 locale별 값은 하단 상세 패널에서 `JTextArea`로 편집한다.
- 그리드 셀은 직접 편집하지 않고, 상세 패널이 편집 진입점이다.
- `Ctrl+C`는 마지막 클릭 셀 값만 복사하고, 그리드에서 `Ctrl+V`는 무시한다.
- 상세 패널 입력 필드에서는 기본 복사/붙여넣기 동작을 유지한다.

### 추가, 편집, 삭제

- `추가`는 `다국어 추가` 팝업에서 key를 입력받는다.
- `편집`은 `다국어 편집` 팝업에서 key 이름을 변경한다.
- 일반 번역 key는 빈 key, dot path 형식 오류, 기존 key 중복, dot path 충돌을 검증한다.
- 예외키는 중복과 dot path 검증에서 제외한다.
- 삭제는 즉시 파일에서 지우지 않고 삭제 후보 상태로 표시한다.
- 삭제 취소로 삭제 후보를 되돌릴 수 있다.

### 순서 변경

- 검색어 또는 상태 필터가 켜져 있으면 순서 변경을 비활성화한다.
- 핸들 컬럼에서 시작한 드래그만 row 이동으로 처리한다.
- 같은 최상위 그룹 안에서는 선택 row만 이동한다.
- 다른 최상위 그룹으로 이동하면 선택 row가 속한 최상위 그룹 전체를 이동한다.
- 예외키 row는 최상위 그룹 내부가 아니라 그룹 경계로 스냅된다.
- 드래그 중 이동 대상은 파란색, 실제 이동되는 row 또는 그룹은 주황색으로 표시한다.
- 순서 변경이 발생하면 하단 상태와 적용 요약에 `순서 변경 있음`을 표시한다.

### 적용 및 취소

- 적용 전 현재 다국어 에디터 기준으로 JSON을 재구성할지 확인한다.
- 적용 요약은 `파일 저장`, `생성`, `추가`, `편집`, `삭제`, `순서 변경 있음`을 표시한다.
- count가 0인 항목은 요약에서 표시하지 않는다.
- error가 남아 있으면 적용을 차단한다.
- 취소는 원본 JSON을 다시 로드해 미적용 변경을 버린다.
- 저장되지 않은 상태에서 JSON 탭으로 이동하거나 파일을 닫을 때 확인 팝업을 띄운다.

### 설정

- 프로젝트 단위 설정은 `localeGrid.xml`에 저장된다.
- 설정 항목:
  - `locale 루트 경로`
  - `locale 표시 순서`
  - `예외 키`
  - `JSON 들여쓰기`
- 다국어 에디터 상단의 `예외키` 버튼으로 예외키 목록을 빠르게 변경할 수 있다.
- 예외키 설정을 바꾸면 현재 다국어 에디터를 다시 로드한다.

## 테스트 커버리지

현재 단위 테스트는 다음 영역을 커버한다.

- locale path 및 파일명 패턴 판별
- JSON flatten 순서 보존
- 중복 JSON key 진단
- root-level 예외키 숨김 및 marker anchor 보존
- nested 예외키를 일반 번역 key로 처리
- JSON writer의 root/nested 순서 보존
- root entry list 기반 중복 root entry 출력
- 빈 값 warning과 예외키 row 검증 제외
- 테이블 status 우선순위
- locale value 수정 상태
- editor escaping/unescaping

검증 결과:

- `.\gradlew.bat test --console=plain` 성공
- 실행일: 2026-05-27

## 확인된 주의사항

- README 일부 표현은 현재 UI와 다르다. 예: `행 추가`, `키 이름 변경`, `수정 key 수` 같은 문구는 실제 UI의 `다국어 추가`, `다국어 편집`, `편집`과 맞춰 정리할 필요가 있다.
- Settings 화면 라벨은 `예외 키`로 띄어쓰기 되어 있고, 에디터 버튼/팝업은 `예외키`로 붙여 쓴다. 사용자-facing 용어를 하나로 통일하는 것이 좋다.
- 설정 화면은 `고급 설정` 접이식 패널을 사용한다. 이전 대화에서 고급 설정 접기 제거 이야기가 있었으므로 현재 제품 방향과 맞는지 재확인이 필요하다.
- `commentKeys`에서 `exceptionKeys`로의 마이그레이션은 구현하지 않는다. 기존 프로젝트 설정에 오래된 값이 남아 있어도 자동 변환하지 않는다.
- hidden 예외키 marker는 anchor root가 저장 시점에 없으면 저장 결과에서 제거된다.
- 예외키만 있는 JSON처럼 anchor를 잡을 non-exception root entry가 없으면 hidden marker로 보존되지 않는다.
- 현재 테스트는 UI 직접 조작, 실제 IDE 탭 전환, 드래그 시각 효과, 팝업 표시 크기까지 자동 검증하지 않는다.

## 권장 후속 점검

- README 용어를 현재 UI 기준으로 업데이트한다.
- `예외키`/`예외 키` 표기를 통일한다.
- 설정 화면의 고급 설정 접기 유지 여부를 결정한다.
- `.\gradlew.bat test --console=plain`로 전체 단위 테스트를 정기 확인한다.
- 샌드박스 IDE에서 다음 수동 시나리오를 확인한다:
  - 일반 key 추가/편집/삭제 후 적용
  - 예외키 추가 후 적용 시 hidden 처리
  - root-level 중복 예외키 보존
  - drag/drop으로 같은 그룹 row 이동
  - drag/drop으로 다른 그룹 전체 이동
  - 검색/필터 상태에서 이동 비활성화
  - 셀 클릭 후 `Ctrl+C`가 셀 값만 복사되는지 확인
