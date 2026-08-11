# Reward Platform Backend

Spring Boot 기반의 **리워드 및 포인트 관리 백엔드 시스템**입니다.

사용자가 특정 리워드 이벤트를 신청하면 리워드 이력이 생성되고, 지급된 포인트가 사용자의 지갑 잔액에 반영됩니다.

단순한 CRUD 구현에 그치지 않고, **중복 요청에 대한 멱등성 보장, 지갑 잔액의 동시성 제어, JWT 인증, 예외 처리, DB 마이그레이션, 테스트 코드**를 중심으로 구현했습니다.

---

## 1. 프로젝트 개요

### 핵심 기능

* 회원가입
* JWT 기반 로그인 및 인증
* 리워드 이벤트 관리
* 리워드 지급
* 리워드 지급 이력 조회
* 사용자 포인트 지갑 관리
* 중복 요청에 대한 멱등성 처리
* 비활성화된 리워드 이벤트 검증
* Wallet Optimistic Lock을 이용한 동시성 제어
* 공통 예외 처리
* Flyway 기반 DB 마이그레이션
* Repository / Service / Integration Test

### 핵심 리워드 지급 흐름

```text
Client
  │
  │ JWT + Reward Event ID + Idempotency Key
  ▼
Controller
  │
  ▼
Reward Service
  │
  ├── 중복 요청 확인
  ├── 사용자 확인
  ├── 리워드 이벤트 확인
  ├── 이벤트 활성화 여부 확인
  ├── 지갑 확인
  ├── RewardHistory 생성
  └── Wallet 잔액 증가
  │
  ▼
PostgreSQL
```

---

# 2. 기술 스택

| 구분                | 기술                                 |
| ----------------- | ---------------------------------- |
| Language          | Java                               |
| Framework         | Spring Boot                        |
| Security          | Spring Security, JWT               |
| ORM               | Spring Data JPA, Hibernate         |
| Database          | PostgreSQL                         |
| Migration         | Flyway                             |
| Build             | Maven                              |
| Test              | JUnit 5, Mockito, Spring Boot Test |
| API Documentation | OpenAPI                            |
| Container         | Docker, Docker Compose             |
| Version Control   | Git                                |

---

# 3. 아키텍처

도메인별로 패키지를 분리하고 각 도메인 내부에서 Controller → Service → Repository 계층을 구성했습니다.

```text
com.tanigawa.rewardplatform
│
├── auth
│   ├── controller
│   ├── dto
│   ├── jwt
│   └── service
│
├── config
│
├── exception
│
├── reward
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── user
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
└── wallet
    ├── controller
    ├── dto
    ├── entity
    ├── repository
    └── service
```

기본적인 요청 흐름은 다음과 같습니다.

```text
HTTP Request
     ↓
Controller
     ↓
Service
     ↓
Repository
     ↓
PostgreSQL
```

Controller는 HTTP 요청/응답을 담당하고, 실제 비즈니스 규칙은 Service 계층에서 처리하도록 분리했습니다.

---

# 4. 도메인 모델

주요 도메인은 `User`, `Wallet`, `RewardEvent`, `RewardHistory`로 구성되어 있습니다.

```text
User
 │
 ├── 1 : 1 ── Wallet
 │
 └── 1 : N ── RewardHistory ── N : 1 ── RewardEvent
```

### User

서비스를 이용하는 사용자를 나타냅니다.

* 회원가입
* 로그인
* 리워드 지급 대상 식별

등에 사용됩니다.

### Wallet

사용자가 보유한 포인트 잔액을 관리합니다.

```text
Wallet
├── id
├── user_id
├── balance
├── created_at
├── updated_at
└── version
```

`version` 필드를 이용해 JPA의 Optimistic Lock을 적용했습니다.

### RewardEvent

사용자가 지급받을 수 있는 리워드 종류를 나타냅니다.

예:

```text
SIGNUP
FIRST_PURCHASE
PROMOTION
```

주요 정보:

* 이름
* 설명
* 지급 포인트
* 활성화 여부
* 생성/수정 시간

### RewardHistory

실제 리워드 지급 내역을 기록합니다.

```text
RewardHistory
├── id
├── user
├── reward_event
├── points
├── status
└── idempotency_key
```

리워드 지급 결과를 저장하여 중복 요청 처리와 지급 이력 관리에 사용합니다.

---

# 5. 핵심 기능 ① 리워드 지급

리워드 지급은 이 프로젝트의 핵심 비즈니스 로직입니다.

```text
1. 요청 수신
       ↓
2. Idempotency Key 확인
       ↓
3. 사용자 조회
       ↓
4. RewardEvent 조회
       ↓
5. 이벤트 활성화 여부 확인
       ↓
6. Wallet 조회
       ↓
7. RewardHistory 생성
       ↓
8. Reward 처리
       ↓
9. Wallet 잔액 증가
       ↓
10. 결과 반환
```

예를 들어 사용자가 100포인트짜리 이벤트를 신청하면:

```text
Wallet Balance
      │
      │ +100
      ▼
RewardHistory 생성
      │
      ▼
Wallet Balance 증가
```

리워드 지급과 지갑 잔액 변경이 서로 다른 작업으로 분리되어 잘못 처리되지 않도록 Service 계층에서 하나의 비즈니스 흐름으로 관리했습니다.

---

# 6. 핵심 기능 ② 멱등성 처리

리워드 지급 API에서는 **중복 요청 방지**가 중요합니다.

네트워크 지연이나 클라이언트 재시도 때문에 동일한 요청이 여러 번 전달될 수 있기 때문입니다.

예를 들어 같은 요청이 두 번 들어온 경우:

```text
첫 번째 요청
Idempotency-Key = ABC
        │
        ▼
RewardHistory 생성
        │
        ▼
+100 포인트


두 번째 요청
Idempotency-Key = ABC
        │
        ▼
기존 RewardHistory 조회
        │
        ▼
기존 결과 반환
```

두 번째 요청에서는 포인트를 다시 지급하지 않고 기존 처리 결과를 반환합니다.

이를 통해 동일한 논리적 요청이 여러 번 전송되더라도 중복 지급을 방지합니다.

---

# 7. 핵심 기능 ③ Wallet 동시성 제어

Wallet의 잔액은 여러 요청에서 동시에 변경될 수 있는 공유 데이터입니다.

예를 들어 같은 사용자의 Wallet에 동시에 리워드가 지급될 수 있습니다.

```text
Transaction A          Transaction B
     │                      │
balance = 100          balance = 100
     │                      │
   +100                   +200
     │                      │
     ▼                      ▼
  version 2            version 충돌
```

이를 처리하기 위해 Wallet에 JPA `@Version`을 적용했습니다.

```text
Wallet
 ├── balance
 └── version
```

Optimistic Lock을 통해 동일한 데이터를 동시에 수정할 때 변경 충돌을 감지할 수 있도록 했습니다.

또한 충돌 상황을 `WalletConflictException`으로 분리하여 API 레벨에서 일관된 오류 응답을 반환할 수 있도록 구성했습니다.

---

# 8. 인증 및 보안

Spring Security와 JWT를 이용해 인증을 구현했습니다.

### 로그인

```text
Client
  │
  │ email + password
  ▼
AuthController
  │
  ▼
AuthService
  │
  ├── 사용자 조회
  └── 비밀번호 검증
  │
  ▼
TokenProvider
  │
  ▼
JWT 발급
```

### 인증된 API 요청

```text
Client
  │
  │ Authorization: Bearer <JWT>
  ▼
JwtAuthenticationFilter
  │
  ▼
JWT 검증
  │
  ▼
SecurityContext
  │
  ▼
Controller
```

비밀번호는 평문으로 저장하지 않고 Password Encoder를 이용해 안전하게 저장합니다.

---

# 9. 예외 처리

비즈니스 상황별로 Custom Exception을 정의했습니다.

주요 예외:

```text
UserNotFoundException
RewardEventNotFoundException
RewardHistoryNotFoundException
RewardDisabledException
WalletNotFoundException
WalletConflictException
WrongEmailOrPasswordException
```

각 Controller에서 예외를 개별적으로 처리하는 대신 `GlobalExceptionHandler`에서 공통으로 처리하도록 구성했습니다.

예:

```json
{
  "timestamp": "2026-08-11T16:00:00",
  "status": 404,
  "error": "REWARD_EVENT_NOT_FOUND",
  "message": "Reward event not found.",
  "path": "/api/reward-events/999"
}
```

이를 통해 API 전체에서 일관된 오류 응답 형식을 유지할 수 있습니다.

---

# 10. 데이터베이스 및 마이그레이션

PostgreSQL을 사용하며 데이터베이스 스키마는 Flyway로 관리합니다.

현재 마이그레이션:

```text
V1__Create_user_ans_wallet.sql
V2__Create_reward_events.sql
V3__Create_reward_history.sql
V4__rename_reward_history_to_reward_histories.sql
V5__add_version_to_wallets.sql
```

Hibernate의 자동 스키마 변경 대신 다음 설정을 사용했습니다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

즉, 애플리케이션 실행 시 Hibernate가 임의로 테이블을 변경하지 않고 현재 Entity와 DB 스키마가 일치하는지 검증합니다.

스키마 변경은 Flyway migration을 통해 명시적으로 관리합니다.

```text
Entity 변경
    ↓
Migration SQL 작성
    ↓
Flyway 실행
    ↓
PostgreSQL Schema 변경
```

이를 통해 개발 환경과 다른 환경에서도 동일한 DB 변경 이력을 재현할 수 있도록 구성했습니다.

---

# 11. 테스트

테스트는 테스트 대상에 따라 다음과 같이 분리했습니다.

```text
src/test
└── java
    └── com.tanigawa.rewardplatform
        ├── reward
        │   ├── integration
        │   ├── repository
        │   └── service
        ├── user
        └── wallet
```

## Service Test

비즈니스 로직을 중심으로 테스트합니다.

```text
RewardEventServiceTest
RewardHistoryServiceTest
RewardDisabledEventTest
UserNotFoundTest
WalletNotFoundTest
IncreaseBalanceTwiceTest
```

검증하는 주요 상황:

* 정상적인 리워드 지급
* 비활성화된 리워드 이벤트
* 존재하지 않는 사용자
* 존재하지 않는 Wallet
* 중복 요청
* Wallet 잔액 증가
* 예외 상황

Mockito를 이용해 Repository 등의 외부 의존성을 Mocking하고 Service의 비즈니스 로직을 독립적으로 검증했습니다.

---

## Repository Test

`RewardEventRepositoryTest`에서는 JPA Repository의 실제 persistence 동작을 검증합니다.

예:

```text
RewardEvent
    ↓
Repository.save()
    ↓
PostgreSQL
    ↓
Repository.findByName()
```

이를 통해 단순히 Mock 객체가 예상대로 동작하는지를 확인하는 것이 아니라 실제 JPA Repository와 DB 매핑이 정상적으로 동작하는지 검증합니다.

---

## Integration Test

Integration Test에서는 실제 HTTP 요청부터 DB 처리까지 전체 흐름을 검증합니다.

```text
HTTP Request
     ↓
Controller
     ↓
Service
     ↓
Repository
     ↓
PostgreSQL
     ↓
HTTP Response
```

현재 구현된 핵심 Integration Test:

```text
claimRewardSuccessIntegrationTest
```

이를 통해 개별 Service 테스트만으로는 확인하기 어려운 Controller → Service → Repository 전체 연결을 검증합니다.

---

# 12. API

주요 API는 다음과 같습니다.

### 회원가입

```http
POST /api/users
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

응답으로 JWT Access Token을 발급합니다.

### 리워드 지급

```http
POST /api/reward-events/claims
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "rewardEventId": 1,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Wallet 조회

```http
GET /api/wallet/users/{userId}
Authorization: Bearer <JWT>
```

예시:

```json
{
  "userId": 1,
  "balance": 1000
}
```

---

# 13. 프로젝트 실행

## 요구사항

* Java
* Maven
* Docker
* Docker Compose
* PostgreSQL

## PostgreSQL 실행

```bash
docker compose up -d
```

컨테이너 확인:

```bash
docker compose ps
```

## Spring Boot 실행

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

애플리케이션 실행 시 Flyway migration이 자동으로 적용됩니다.

---

# 14. 테스트 실행

전체 테스트:

```bash
./mvnw test
```

Windows:

```bash
mvnw.cmd test
```

특정 테스트 실행:

```bash
./mvnw -Dtest=RewardHistoryServiceTest test
```

---

# 15. API 문서

OpenAPI 설정을 적용했습니다.

```text
OpenAPIConfig.java
```

애플리케이션 실행 후 Swagger UI를 통해 API를 확인하고 테스트할 수 있습니다.

---

# 16. 프로젝트 구조

```text
src
├── main
│   ├── java
│   │   └── com.tanigawa.rewardplatform
│   │       ├── auth
│   │       ├── common
│   │       ├── config
│   │       ├── exception
│   │       ├── notification
│   │       ├── reward
│   │       ├── user
│   │       └── wallet
│   │
│   └── resources
│       ├── application.yaml
│       └── db
│           └── migration
│
└── test
    ├── java
    │   └── com.tanigawa.rewardplatform
    │       ├── reward
    │       │   ├── integration
    │       │   ├── repository
    │       │   └── service
    │       ├── user
    │       └── wallet
    │
    └── resources
        └── application-test.yml
```

---

# 17. 주요 설계 포인트

### 1. 멱등성

동일한 리워드 요청이 반복되어도 중복 지급되지 않도록 `Idempotency Key`를 사용했습니다.

### 2. 동시성 제어

Wallet 잔액 변경 과정에서 발생할 수 있는 동시 업데이트 문제를 `@Version` 기반 Optimistic Lock으로 처리했습니다.

### 3. 계층 분리

Controller는 HTTP 처리, Service는 비즈니스 로직, Repository는 데이터 접근을 담당하도록 역할을 분리했습니다.

### 4. 예외 처리 중앙화

Custom Exception과 `GlobalExceptionHandler`를 이용해 일관된 오류 응답을 제공합니다.

### 5. DB 스키마 버전 관리

Flyway를 사용하여 DB 변경 사항을 SQL migration으로 관리하고, Hibernate는 `validate` 모드로 사용했습니다.

### 6. 테스트 계층 분리

Service Unit Test, Repository Test, Integration Test를 구분하여 각 계층의 책임에 맞는 테스트를 작성했습니다.

---

# 18. 테스트를 통해 검증한 주요 시나리오

| 시나리오             | 테스트                                 |
| ---------------- | ----------------------------------- |
| 정상적인 리워드 지급      | `RewardEventServiceTest`            |
| 중복 요청            | `RewardHistoryServiceTest`          |
| 비활성 리워드          | `RewardDisabledEventTest`           |
| 존재하지 않는 사용자      | `UserNotFoundTest`                  |
| 존재하지 않는 Wallet   | `WalletNotFoundTest`                |
| Wallet 잔액 증가     | `IncreaseBalanceTwiceTest`          |
| Repository 조회/저장 | `RewardEventRepositoryTest`         |
| 전체 API 흐름        | `claimRewardSuccessIntegrationTest` |

---

# 19. 향후 개선 사항

현재 구조를 기반으로 다음과 같은 개선이 가능합니다.

* Testcontainers를 이용한 격리된 PostgreSQL Integration Test
* Redis 기반 분산 환경 Idempotency 처리
* Refresh Token
* Role 기반 권한 관리
* Reward History Pagination
* DB Index 최적화
* 구조화된 Logging
* Metrics 및 Monitoring
* Docker 기반 애플리케이션 배포
* CI/CD Pipeline
* 동시 리워드 요청에 대한 Load Test
* Kafka 기반 Event-driven Architecture

---

# 20. 프로젝트를 통해 구현한 핵심

이 프로젝트의 핵심은 단순한 회원/리워드 CRUD가 아니라 **포인트 지급이라는 상태 변경 작업을 안정적으로 처리하는 것**입니다.

특히 다음 문제를 직접 구현하고 테스트했습니다.

```text
중복 요청
    ↓
Idempotency

동시 Wallet 변경
    ↓
Optimistic Lock

잘못된 비즈니스 요청
    ↓
Custom Exception

DB Schema 변경
    ↓
Flyway Migration

각 계층의 정상 동작
    ↓
Unit / Repository / Integration Test
```

이를 통해 **Spring Boot 기반 REST API 개발부터 JPA, PostgreSQL, 인증, 트랜잭션 처리, 동시성 제어, DB 마이그레이션, 테스트까지 백엔드 서비스의 기본적인 개발 사이클을 경험하는 것**을 목표로 했습니다.
