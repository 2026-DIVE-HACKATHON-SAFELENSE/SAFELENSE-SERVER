# 내 집 등록·부분 수정 API 설계

## 목표

로그인 사용자가 현재 집 정보를 한 건 등록하고, 필요한 필드만 부분 수정하며, 자신의 정보만 조회할 수 있게 한다.

## 범위

- `GET /api/v1/me/property`로 현재 집 정보를 조회한다.
- `POST /api/v1/me/property`로 최초 정보를 등록한다.
- `PATCH /api/v1/me/property`로 기존 정보의 일부를 JSON Merge Patch 형식으로 수정한다.
- 사용자당 현재 집 한 건만 저장한다.

프론트엔드 화면, 월세·상세 주소, 삭제 API, 주택 변경 이력은 이번 범위에서 제외한다.

## 데이터 모델

`home_properties` 테이블은 `user_id`를 유니크 키로 사용한다. 주소, 만원 단위 보증금, 건물 유형, 선택 정보인 임대인명과 계약 예정일을 저장한다. 사용자 삭제 시 관련 정보도 삭제되도록 외래 키에 `ON DELETE CASCADE`를 적용한다.

건물 유형은 `MULTI_FAMILY`, `APARTMENT`, `OFFICETEL`, `DETACHED_HOUSE`로 제한한다. 엔티티는 기존 프로젝트 관례대로 사용자 연관 객체 대신 `userId: Long`을 저장한다.

## API 계약

조회 결과가 없으면 `200 {"property": null}`을 반환한다. 조회 결과가 있으면 `property` 객체에 `id`, `address`, `depositAmount`, `buildingType`, `landlordName`, `plannedContractDate`를 담는다.

POST는 주소, 보증금, 건물 유형을 필수로 받는다. 성공하면 `201 Created`, 이미 정보가 있으면 `409 PROPERTY_ALREADY_EXISTS`를 반환한다.

PATCH는 `application/merge-patch+json`만 소비한다. 생략한 필드는 유지하고 선택 필드의 명시적 `null`은 값을 삭제한다. 필수 필드의 `null`, 빈 객체, 알 수 없는 필드, 잘못된 enum·날짜·값은 `400 INVALID_REQUEST`다. 수정할 정보가 없으면 `404 PROPERTY_NOT_FOUND`다.

## 처리 흐름

JWT 인증 필터가 만든 `Authentication.principal`의 사용자 ID를 컨트롤러가 서비스에 전달한다. 서비스는 해당 사용자 ID로만 조회·생성·수정한다. POST는 기존 행이 없는 경우에만 저장하고, PATCH는 기존 행에 전달된 필드만 반영한다.

PATCH 파서는 JSON 필드 존재 여부를 기준으로 각 필드를 유지, 값 설정, 삭제 상태로 변환한다. 선택 필드만 삭제 상태를 허용한다.

## 검증

- 서비스 테스트로 조회, 최초 생성, 중복 생성, 부분 수정, 선택 정보 삭제, 미등록 수정을 검증한다.
- MVC 테스트로 응답 계약, principal 전달, POST 검증, Merge Patch 해석과 오류 응답을 검증한다.
- 마이그레이션 테스트로 V3 파일과 핵심 제약 조건을 확인한다.
- `./gradlew test --tests 'com.safelense.property.*'`, `./gradlew test`, `./gradlew bootJar`를 모두 통과시킨다.
