# 분석 케이스 입력 API 설계

## 목표

로그인 사용자가 사진의 `계약 전`, `계약 중`, `계약 후` 흐름에 따라 분석 케이스를 만들고, 단계별 서류와 체크리스트를 선택적으로 입력·저장할 수 있게 한다.

사진과 노션 명세가 다르면 사진을 우선한다. 이번 범위는 분석 입력 수집까지이며 위험 점수 계산과 결과 생성은 포함하지 않는다.

## 범위

- `GET /api/v1/analysis-templates/{stage}`로 단계별 서류 슬롯과 탭별 체크리스트를 조회한다.
- `POST /api/v1/analysis-cases`로 주택과 계약 단계에 대한 분석 케이스를 생성한다.
- `GET /api/v1/analysis-cases/{caseId}`로 서류·체크리스트 입력 상태를 조회한다.
- `POST /api/v1/analysis-cases/{caseId}/documents`로 PDF 또는 이미지를 한 서류 슬롯에 업로드한다.
- `DELETE /api/v1/analysis-cases/{caseId}/documents/{documentId}`로 업로드한 서류를 삭제한다.
- `PUT /api/v1/analysis-cases/{caseId}/checklist`로 현재 체크리스트 답변 전체를 교체한다.

`POST /api/v1/analysis-cases/{caseId}/analyze`, 위험 규칙, 분석 결과, 분석 이력, PDF 리포트, OCR, 문서 내용 추출, 관리자용 템플릿 편집은 제외한다. 위험 분석은 연습 데이터가 제공된 뒤 별도 설계와 구현 단위로 진행한다.

## 사진 우선 화면 계약

계약 단계는 `BEFORE_CONTRACT`, `DURING_CONTRACT`, `AFTER_CONTRACT` 세 값으로 제한한다. 각 단계의 서류 화면은 6개 슬롯과 `uploadedCount/6` 진행률을 제공한다.

체크리스트는 평면 문항 목록이 아니라 사진 상단 탭을 표현하는 섹션 목록으로 반환한다. 각 섹션은 안정적인 `sectionKey`, 표시 문구, 순서, 문항 목록을 가진다. 문항은 안정적인 `itemKey`, 표시 문구와 순서를 가지며 사용자는 사진의 원형 체크 표시를 불리언 값으로 저장한다.

`다음 단계`는 프론트엔드 화면 이동이므로 별도 서버 API를 만들지 않는다. 서류나 답변이 없어도 다음 화면으로 이동할 수 있다.

사진의 `AI 분석 시작하기` 버튼은 이번 서버 범위에서 동작시키지 않는다. 프론트엔드는 체크리스트 저장까지 호출하고 분석 기능을 준비 중인 상태로 처리한다.

## 데이터 모델

### 분석 케이스

`analysis_cases`는 `id`, `user_id`, `property_id`, `stage`, `template_version`, 생성·수정 시각을 저장한다.

케이스 생성 시 주택이 인증 사용자 소유인지 검증한다. 한 사용자가 같은 주택과 단계에 여러 케이스를 만들 수 있으며 각 입력 시도는 독립된 기록이 된다.

### 분석 문서

`analysis_documents`는 `id`, `case_id`, `document_type`, 원본 파일명, MIME 타입, 파일 크기, 파일 바이트, 생성·수정 시각을 저장한다. `(case_id, document_type)`은 유일하며 같은 슬롯에 재업로드하면 기존 문서를 교체한다.

MVP에서는 별도 파일 저장 인프라를 도입하지 않고 MySQL `MEDIUMBLOB`에 저장한다. 허용 형식은 PDF, JPEG, PNG이며 파일당 최대 크기는 10MiB다. 파일 내용은 해석하지 않는다.

### 체크리스트 답변

`analysis_checklist_answers`는 `case_id`, `item_key`, `checked` 불리언 값과 수정 시각을 저장한다. `PUT` 요청은 케이스의 기존 답변을 요청의 전체 답변 집합으로 교체한다. 빈 배열과 일부 답변을 허용하며 템플릿에 없는 `itemKey`와 중복된 `itemKey`는 거절한다.

## API 동작

### 템플릿 조회

`GET /api/v1/analysis-templates/{stage}`는 단계, 템플릿 버전, 서류 슬롯 6개, 체크리스트 섹션과 문항을 반환한다. 알 수 없는 단계는 `400 INVALID_STAGE`다.

템플릿 카탈로그는 애플리케이션 코드의 불변 데이터로 관리한다. 운영 중 편집이나 데이터베이스 관리는 지원하지 않는다.

### 케이스 생성

`POST /api/v1/analysis-cases`는 `stage`, `propertyId`를 받는다. 주택이 없거나 다른 사용자 소유면 정보 노출을 막기 위해 `404 PROPERTY_NOT_FOUND`를 반환한다. 성공 시 `201 Created`와 새 케이스 ID, 단계, 주택 ID, 템플릿 버전을 반환한다.

### 케이스 조회

`GET /api/v1/analysis-cases/{caseId}`는 본인 케이스만 반환한다. 응답에는 단계, 템플릿 버전, 6개 서류 슬롯의 업로드 상태, `uploadedCount`, 현재 체크리스트 답변을 포함한다. 다른 사용자 소유이거나 존재하지 않으면 모두 `404 ANALYSIS_CASE_NOT_FOUND`다.

서류 슬롯은 템플릿 순서를 유지하고 업로드가 없는 슬롯도 반환한다. 프론트엔드는 별도 조합 없이 응답만으로 사진의 `0/6` 화면을 그릴 수 있다.

### 문서 업로드

`POST /api/v1/analysis-cases/{caseId}/documents`는 `multipart/form-data`의 `documentType`, `file`을 받는다. 현재 단계에 없는 문서 종류, 빈 파일, 허용하지 않은 MIME 타입은 `400 INVALID_DOCUMENT`, 10MiB 초과 파일은 `413 DOCUMENT_TOO_LARGE`로 반환한다.

같은 `documentType`에 이미 파일이 있으면 메타데이터와 파일 바이트를 교체한다. 성공 시 문서 ID, 문서 종류, 원본 파일명, MIME 타입, 파일 크기와 새 `uploadedCount`를 반환한다.

### 문서 삭제

`DELETE /api/v1/analysis-cases/{caseId}/documents/{documentId}`는 본인 케이스와 해당 케이스 소속 문서를 함께 확인한다. 존재하지 않거나 소유자가 다르면 `404 ANALYSIS_DOCUMENT_NOT_FOUND`다. 성공 시 `204 No Content`를 반환한다.

### 체크리스트 저장

`PUT /api/v1/analysis-cases/{caseId}/checklist`는 `answers: [{ itemKey, checked }]` 형태로 현재 답변 집합 전체를 받는다. 빈 배열, 일부 답변, 모든 답변을 허용한다. 저장 성공 시 현재 답변을 템플릿 순서로 반환한다.

요청의 `itemKey`가 현재 단계 템플릿에 없거나 한 요청에 중복되면 `400 INVALID_CHECKLIST`다.

## 사용자 격리

모든 케이스·문서 조회와 변경은 ID와 인증 사용자 ID를 함께 조건으로 사용한다. 다른 사용자 데이터의 존재 여부를 응답으로 구분하지 않는다.

주택 소유권은 `propertyId`와 인증 사용자 ID로 확인한다. 케이스 생성 이후에도 모든 API에서 케이스 소유자를 다시 검증한다.

## 동시성

같은 문서 슬롯에 동시에 업로드하는 경우 `(case_id, document_type)` 유일 제약을 최종 기준으로 삼는다. 서비스는 슬롯 행을 조회한 뒤 생성하거나 교체하고, 유일 제약 충돌 시 다시 조회해 마지막 요청의 파일로 갱신한다.

체크리스트 `PUT`은 한 트랜잭션에서 기존 답변을 삭제하고 새 답변 집합을 저장한다. 요청 단위로 전체 교체되며 부분적으로 저장된 상태를 노출하지 않는다.

## 오류 처리

- 잘못된 단계는 `400 INVALID_STAGE`로 반환한다.
- 잘못된 문서 종류·빈 파일·허용하지 않은 형식은 `400 INVALID_DOCUMENT`로 반환한다.
- 파일 크기 초과는 `413 DOCUMENT_TOO_LARGE`로 반환한다.
- 잘못되거나 중복된 체크리스트 문항은 `400 INVALID_CHECKLIST`로 반환한다.
- 본인 소유가 아닌 주택·케이스·문서는 존재하지 않는 것과 같은 `404`로 반환한다.

## 테스트 전략

- 마이그레이션 테스트로 외래 키, 문서 슬롯 유일 제약과 `MEDIUMBLOB` 타입을 확인한다.
- 템플릿 테스트로 세 단계, 단계별 서류 슬롯 6개, 섹션·문항 순서와 안정적인 키를 검증한다.
- 서비스 테스트로 주택·케이스 사용자 격리, 문서 생성·교체·삭제와 체크리스트 전체 교체를 검증한다.
- 빈 체크리스트, 일부 체크리스트, 업로드 없는 케이스 조회를 정상 상태로 검증한다.
- MVC 테스트로 multipart 계약, 10MiB 제한, 요청 검증, 상태 코드와 응답 구조를 검증한다.
- 관련 패키지 테스트, 전체 `./gradlew test`, `./gradlew bootJar`를 순서대로 실행한다.

## 완료 기준

- 사진의 세 단계, 단계별 서류 6종, 탭형 체크리스트와 부분 입력 흐름을 API로 지원한다.
- 업로드 상태와 답변을 저장하고 다시 조회했을 때 동일하게 복원한다.
- 빈 서류와 빈·부분 체크리스트를 정상 상태로 처리한다.
- 같은 문서 슬롯의 재업로드는 기존 문서를 교체한다.
- 다른 사용자의 주택·케이스·문서에 접근할 수 없다.
- 관련 테스트, 전체 테스트와 실행 JAR 생성이 통과한다.
