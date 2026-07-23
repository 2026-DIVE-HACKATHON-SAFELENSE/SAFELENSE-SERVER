# 분석 이력·리포트·사용자 API 설계

## 목표

분석 실행을 제외하고 로그인 사용자가 저장된 분석 결과를 목록·상세로 조회하고 PDF 리포트로 내려받으며, 자신의 프로필과 온보딩 상태를 조회·변경할 수 있게 한다.

## 범위

- `GET /api/v1/analyses`
- `GET /api/v1/analyses/{analysisId}`
- `GET /api/v1/analyses/{analysisId}/report.pdf`
- `GET /api/v1/me`
- `PATCH /api/v1/me/onboarding`

`POST /api/v1/analysis-cases/{caseId}/analyze`, 위험 규칙 계산, OCR, 외부 AI·공공데이터 연동은 이번 범위에서 제외한다.

## 분석 결과 모델

향후 분석 실행 API가 같은 모델에 결과를 저장할 수 있도록 `analysis_results` 테이블을 추가한다. 결과는 사용자와 분석 케이스에 귀속되며 케이스당 한 건만 허용한다.

저장 필드는 결과 ID, 케이스 ID, 사용자 ID, 주택 ID, 계약 단계, 위험 점수, 위험 등급, 신뢰도, 요약, 줄바꿈으로 구분한 발견 사항과 권고사항, 규칙 버전, 분석 시각이다. 이번 작업에는 결과 생성 경로를 추가하지 않는다.

위험 점수는 근거가 없을 때 `null`일 수 있다. 위험 등급은 `UNKNOWN`, `LOW`, `MEDIUM`, `HIGH` 네 값으로 제한한다. 신뢰도는 0부터 100까지의 정수다.

## 분석 이력 조회

`GET /api/v1/analyses`는 인증 사용자의 결과만 ID 내림차순으로 반환한다. `cursor`는 마지막으로 받은 결과 ID, `size`는 기본 20개이고 1부터 100까지 허용한다. `stage`는 선택 값이며 기존 `AnalysisStage` 세 값만 허용한다.

응답은 `analyses`, `nextCursor`, `hasNext`를 포함한다. 각 항목에는 결과 ID, 케이스 ID, 주택 ID, 단계, 점수, 등급, 신뢰도, 요약과 분석 시각을 반환한다.

잘못된 커서·크기·단계는 `400 INVALID_REQUEST`다.

## 분석 결과 상세 조회

`GET /api/v1/analyses/{analysisId}`는 인증 사용자 소유 결과만 반환한다. 목록 항목 필드에 `findings`, `recommendations`, `ruleVersion`을 추가한다.

존재하지 않거나 다른 사용자 소유인 결과는 모두 `404 ANALYSIS_NOT_FOUND`로 반환해 데이터 존재 여부를 숨긴다.

## PDF 리포트

`GET /api/v1/analyses/{analysisId}/report.pdf`는 상세 응답과 같은 저장 결과로 PDF를 즉시 생성한다. 응답은 `application/pdf`이며 파일명은 `safelense-analysis-{analysisId}.pdf`다.

PDF에는 분석 ID, 계약 단계, 점수·등급·신뢰도, 요약, 발견 사항, 권고사항과 분석 시각을 포함한다. 별도의 PDF 파일이나 BLOB은 저장하지 않는다.

PDF 생성에는 Java 21 이상을 지원하는 OpenPDF 3.0.5를 사용한다. 한글이 실행 환경에 관계없이 표시되도록 Nanum Gothic Coding 폰트를 WebJar로 포함하고 PDF에 임베드한다. 템플릿 엔진과 HTML 렌더러는 추가하지 않는다.

## 사용자 API

`GET /api/v1/me`는 사용자 ID, 닉네임, 프로필 이미지 URL과 온보딩 완료 여부를 반환한다.

`PATCH /api/v1/me/onboarding`은 JSON 본문의 `onboardingCompleted` 불리언 값으로 현재 상태를 변경하고 변경된 사용자 응답을 반환한다. 본문 누락이나 잘못된 타입은 `400 INVALID_REQUEST`다.

JWT에 존재하지만 사용자 행이 없는 경우 두 API 모두 `404 USER_NOT_FOUND`를 반환한다.

## 사용자 격리와 오류 처리

모든 분석 결과 조회는 결과 ID와 인증 사용자 ID를 함께 조건으로 사용한다.

- 잘못된 목록 파라미터는 `400 INVALID_REQUEST`다.
- 잘못된 온보딩 요청은 `400 INVALID_REQUEST`다.
- 분석 결과가 없거나 다른 사용자 소유이면 `404 ANALYSIS_NOT_FOUND`다.
- 인증 사용자 행이 없으면 `404 USER_NOT_FOUND`다.

## 테스트 전략

- V6 마이그레이션에서 결과 테이블, 사용자·케이스 외래 키, 케이스 유일 제약과 조회 인덱스를 검증한다.
- 분석 서비스 테스트에서 커서·크기·단계 필터, 다음 커서, 사용자 격리와 상세 변환을 검증한다.
- PDF 테스트에서 `%PDF-` 시그니처와 저장 결과의 주요 텍스트 생성을 검증한다.
- MVC 테스트에서 목록·상세·PDF 상태 코드, 콘텐츠 타입, 다운로드 파일명과 오류 응답을 검증한다.
- 사용자 서비스와 MVC 테스트에서 조회·상태 변경·사용자 없음·요청 검증을 확인한다.
- 관련 패키지 테스트, 전체 `./gradlew test`, `./gradlew bootJar`, `git diff --check`를 실행한다.

## 완료 기준

- 미완료로 표시된 분석 이력·상세·PDF와 사용자 API 5개가 동작한다.
- 분석 목록이 최신순 커서와 단계 필터를 지원한다.
- 다른 사용자의 결과를 조회하거나 다운로드할 수 없다.
- PDF가 저장된 결과와 동일한 정보를 사용한다.
- 프로필과 온보딩 상태를 조회하고 변경할 수 있다.
- 분석 실행 API는 추가하지 않는다.
- 관련 테스트, 전체 테스트와 실행 JAR 생성이 통과한다.
