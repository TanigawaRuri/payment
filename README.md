# Reward Platform Backend

Spring Boot 기반의 **리워드 및 포인트 관리 백엔드 시스템**입니다.

사용자가 리워드 이벤트를 신청하면 지급 이력을 생성하고 Wallet의 포인트 잔액을 증가시키는 서비스를 구현했습니다.

단순 CRUD 구현을 넘어 다음과 같은 백엔드 핵심 문제를 다루는 것을 목표로 했습니다.

* 중복 요청에 대한 **멱등성 보장**
* Wallet 잔액 변경에 대한 **동시성 제어**
* **JWT 기반 인증**
* 일관된 **예외 처리**
* **Flyway 기반 DB Schema 관리**
* Unit / Repository / Integration Test를 통한 **비즈니스 로직 검증**

---

## 1. 프로젝트 개요

### 주요 기능

| 기능                 | 설명                          |
| ------------------ | --------------------------- |
| 회원가입               | 이메일과 비밀번호를 이용한 사용자 등록       |
| 로그인                | Spring Security + JWT 기반 인증 |
| Reward Event       | 지급 가능한 리워드 이벤트 관리           |
| Reward Claim       | 사용자의 리워드 신청 및 포인트 지급        |
| Reward History     | 리워드 지급 이력 관리                |
| Wallet             | 사용자 포인트 잔액 관리               |
| Idempotency        | 동일 요청의 중복 처리 방지             |
| Optimistic Lock    | Wallet 동시 수정 충돌 감지          |
| Exception Handling | 공통 API 오류 응답 처리             |

---

# 2. 기술 스택

| Category          | Technology                         |
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

# 3. Architecture

도메인별 패키지 구조와 Controller → Service → Repository 계층을 기반으로 구성했습니다.

```text id="m3qj5p"
                         Client
                           │
                           ▼
                ┌────────────────────┐
                │ Spring Security     │
                │ JWT Filter          │
                └──────────┬─────────┘
                           │
                           ▼
                ┌────────────────────┐
                │    Controller       │
                │ Auth / User /       │
                │ Reward / Wallet     │
                └──────────┬─────────┘
                           │
                           ▼
                ┌────────────────────┐
                │      Service        │
                │ Business Logic      │
                └──────────┬─────────┘
                           │
                           ▼
                ┌────────────────────┐
                │     Repository      │
                │   Spring Data JPA   │
                └──────────┬─────────┘
                           │
                           ▼
                ┌────────────────────┐
                │     PostgreSQL      │
                └──────────▲─────────┘
                           │
                ┌──────────┴─────────┐
                │       Flyway        │
                │ DB Schema Migration │
                └────────────────────┘
```

### 패키지 구조

```text id="4gup1f"
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

---

# 4. ERD

주요 도메인은 `User`, `Wallet`, `RewardEvent`, `RewardHistory`로 구성됩니다.

```text id="v8qf2e"
┌──────────────────────┐
│        users         │
├──────────────────────┤
│ PK id                │
│ email                │
│ encoded_password     │
│ created_at           │
│ updated_at           │
└──────────┬───────────┘
           │
           │ 1 : 1
           │
┌──────────▼───────────┐
│       wallets        │
├──────────────────────┤
│ PK id                │
│ FK user_id           │
│ balance              │
│ version              │
│ created_at           │
│ updated_at           │
└──────────────────────┘


┌──────────────────────┐
│    reward_events     │
├──────────────────────┤
│ PK id                │
│ name                 │
│ description          │
│ reward_amount        │
│ enabled               │
│ created_at           │
│ updated_at           │
└──────────┬───────────┘
           │
           │ 1 : N
           │
┌──────────▼───────────┐
│   reward_histories   │
├──────────────────────┤
│ PK id                │
│ FK user_id           │
│ FK reward_event_id   │
│ points               │
│ status               │
│ idempotency_key      │
└──────────────────────┘
```

### 주요 관계

* `User : Wallet = 1 : 1`
* `User : RewardHistory = 1 : N`
* `RewardEvent : RewardHistory = 1 : N`

`Wallet.version`은 Optimistic Lock을 위한 필드이며, `RewardHistory.idempotency_key`는 중복 리워드 지급을 방지하기 위한 식별자로 사용합니다.

---

# 5. 핵심 비즈니스 로직

## Reward Claim Flow

리워드 지급은 프로젝트의 핵심 비즈니스 로직입니다.

```text id="7a6q0r"
POST /api/reward-events/claims
            │
            ▼
   Idempotency Key 확인
            │
      ┌─────┴─────┐
      │           │
    존재          없음
      │           │
      ▼           ▼
기존 결과 반환   사용자 조회
                  │
                  ▼
             RewardEvent 조회
                  │
                  ▼
             활성화 여부 확인
                  │
                  ▼
              Wallet 조회
                  │
                  ▼
          RewardHistory 생성
                  │
                  ▼
             Reward 승인
                  │
                  ▼
          Wallet 잔액 증가
                  │
                  ▼
             결과 반환
```

---

# 6. 멱등성 처리

리워드 지급 API는 네트워크 오류나 클라이언트 재시도로 인해 동일 요청이 여러 번 전송될 수 있습니다.

동일한 요청을 여러 번 처리하면 포인트가 중복 지급될 수 있기 때문에 `idempotencyKey`를 사용합니다.

### 중복 요청

```text id="d8w7f4"
Request 1
Idempotency-Key = ABC
       │
       ▼
RewardHistory 생성
       │
       ▼
+100 points


Request 2
Idempotency-Key = ABC
       │
       ▼
기존 RewardHistory 조회
       │
       ▼
기존 결과 반환
```

따라서 동일한 논리적 요청에 대해서는 리워드를 다시 지급하지 않습니다.

### 핵심 코드 개념

```java
Optional<RewardHistory> existing =
        rewardHistoryRepository.findByIdempotencyKey(idempotencyKey);

if (existing.isPresent()) {
    return RewardHistoryResponse.from(existing.get());
}
```

이를 통해 클라이언트의 재시도 요청을 안전하게 처리할 수 있도록 했습니다.

---

# 7. Wallet 동시성 제어

Wallet은 여러 요청에 의해 동시에 변경될 수 있는 공유 데이터입니다.

예를 들어 동일 사용자가 동시에 여러 리워드를 받는 경우 Wallet 잔액 변경이 충돌할 수 있습니다.

이를 위해 Wallet에 JPA `@Version`을 적용했습니다.

```java
@Version
private Long version;
```

### 동시 업데이트

```text id="3d8b7h"
Transaction A                 Transaction B
     │                             │
     │ version = 1                 │
     │                             │
     ▼                             ▼
 balance = 100                 balance = 100
     │                             │
   +100                          +200
     │                             │
     ▼                             ▼
 version → 2                  version mismatch
                                   │
                                   ▼
                           Optimistic Lock Conflict
```

충돌이 발생하면 이를 `WalletConflictException`으로 처리할 수 있도록 구성했습니다.

이를 통해 동시 요청에서 한 트랜잭션의 변경이 다른 트랜잭션에 의해 조용히 덮어써지는 문제를 방지합니다.

---

# 8. 인증

Spring Security와 JWT를 사용하여 인증을 구현했습니다.

### Login

```text id="f0p2av"
Client
  │
  │ email / password
  ▼
AuthController
  │
  ▼
AuthService
  │
  ├── User 조회
  └── Password 검증
  │
  ▼
TokenProvider
  │
  ▼
JWT 발급
```

### Authenticated Request

```text id="g1p6na"
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

비밀번호는 Password Encoder를 통해 암호화하여 저장합니다.

---

# 9. 예외 처리

비즈니스 상황에 따라 Custom Exception을 정의했습니다.

```text id="k4l1zo"
UserNotFoundException
RewardEventNotFoundException
RewardHistoryNotFoundException
RewardDisabledException
WalletNotFoundException
WalletConflictException
WrongEmailOrPasswordException
```

`GlobalExceptionHandler`에서 예외를 공통 처리하여 API 응답 형식을 일관되게 유지합니다.

예:

```json id="zmb7os"
{
  "timestamp": "2026-08-11T16:00:00",
  "status": 404,
  "error": "REWARD_EVENT_NOT_FOUND",
  "message": "Reward event not found.",
  "path": "/api/reward-events/999"
}
```

---

# 10. Database Migration

PostgreSQL Schema는 Flyway로 관리합니다.

현재 Migration:

```text id="6p3jve"
V1__Create_user_ans_wallet.sql
V2__Create_reward_events.sql
V3__Create_reward_history.sql
V4__rename_reward_history_to_reward_histories.sql
V5__add_version_to_wallets.sql
```

Hibernate는 다음 설정을 사용합니다.

```yaml id="z2v6fj"
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Hibernate가 DB Schema를 자동으로 변경하지 않고 Entity와 실제 DB Schema가 일치하는지 검증하도록 구성했습니다.

Schema 변경은 Flyway Migration을 통해 명시적으로 관리합니다.

```text id="n2qz1e"
Entity 변경
    ↓
Migration SQL 작성
    ↓
Flyway 실행
    ↓
PostgreSQL Schema 변경
```

---

# 11. 테스트

테스트는 목적에 따라 세 가지 수준으로 나누었습니다.

```text id="0z9q0c"
Service Test
    │
    ├── Business Logic
    └── Exception Scenario

Repository Test
    │
    └── JPA + Database

Integration Test
    │
    └── Controller → Service → Repository → Database
```

## Service Test

주요 테스트:

```text id="4w8c5n"
RewardEventServiceTest
RewardHistoryServiceTest
RewardDisabledEventTest
UserNotFoundTest
WalletNotFoundTest
IncreaseBalanceTwiceTest
```

검증 대상:

* 정상적인 리워드 지급
* 중복 요청
* 비활성 리워드
* 존재하지 않는 사용자
* 존재하지 않는 Wallet
* Wallet 잔액 변경
* 예외 처리

Mockito를 사용해 Repository 등의 외부 의존성을 Mocking하고 Service의 비즈니스 로직을 독립적으로 검증했습니다.

---

## Repository Test

`RewardEventRepositoryTest`를 통해 실제 JPA Repository와 DB Mapping을 검증합니다.

```text id="3o0l2q"
RewardEvent
     ↓
Repository.save()
     ↓
Database
     ↓
Repository.findByName()
```

---

## Integration Test

Integration Test에서는 실제 HTTP 요청부터 DB 처리까지 전체 흐름을 검증합니다.

```text id="bq7b7g"
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

현재 핵심 Integration Test:

```text id="6w0z6h"
claimRewardSuccessIntegrationTest
```

---

# 12. API

## 회원가입

```http id="l0l2ce"
POST /api/users
Content-Type: application/json
```

```json id="l9x0s3"
{
  "email": "user@example.com",
  "password": "password123"
}
```

## 로그인

```http id="v4x2n4"
POST /api/auth/login
Content-Type: application/json
```

```json id="6g9q4u"
{
  "email": "user@example.com",
  "password": "password123"
}
```

응답으로 JWT Access Token을 발급합니다.

## 리워드 지급

```http id="t7m8kx"
POST /api/reward-events/claims
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json id="a2c3f6"
{
  "rewardEventId": 1,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Wallet 조회

```http id="z3j1cw"
GET /api/wallet/users/{userId}
Authorization: Bearer <JWT>
```

예시:

```json id="j5f4vl"
{
  "userId": 1,
  "balance": 1000
}
```

---

# 13. API Documentation

OpenAPI를 적용하여 API 명세를 확인할 수 있도록 구성했습니다.

```text id="7h4j9b"
OpenAPIConfig.java
```

애플리케이션 실행 후 Swagger UI에서 API를 확인하고 직접 요청을 테스트할 수 있습니다.

---

# 14. 실행 방법

## 요구사항

* Java
* Maven
* Docker
* Docker Compose
* PostgreSQL

## PostgreSQL 실행

```bash id="m2d1cc"
docker compose up -d
```

상태 확인:

```bash id="9v0m1e"
docker compose ps
```

## Spring Boot 실행

Linux / macOS:

```bash id="0w7s5f"
./mvnw spring-boot:run
```

Windows:

```bash id="x7n9sk"
mvnw.cmd spring-boot:run
```

애플리케이션 시작 시 Flyway Migration이 자동으로 실행됩니다.

---

# 15. 테스트 실행

전체 테스트:

```bash id="c6z0kw"
./mvnw test
```

Windows:

```bash id="q4h6xv"
mvnw.cmd test
```

특정 테스트:

```bash id="5z1g0m"
./mvnw -Dtest=RewardHistoryServiceTest test
```

---

# 16. 프로젝트 구조

```text id="u9q3c8"
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

### 멱등성

`Idempotency Key`를 이용하여 동일한 리워드 요청의 중복 지급을 방지했습니다.

### 동시성 제어

Wallet의 `@Version`을 이용한 Optimistic Lock으로 동시 업데이트 충돌을 감지합니다.

### 계층 분리

Controller, Service, Repository의 역할을 분리하여 HTTP 처리와 비즈니스 로직, 데이터 접근 로직을 구분했습니다.

### 예외 처리

Custom Exception과 `GlobalExceptionHandler`를 이용하여 API 전체에서 일관된 오류 응답을 제공합니다.

### DB Schema 관리

Flyway를 이용해 Schema 변경을 버전별 Migration으로 관리하고 Hibernate는 `validate` 모드로 설정했습니다.

### 테스트 전략

Service Unit Test, Repository Test, Integration Test를 분리하여 각 계층의 책임에 맞게 검증했습니다.

---

# 18. 향후 개선 사항

현재 구조를 기반으로 다음과 같은 개선을 진행할 수 있습니다.

* Testcontainers를 이용한 PostgreSQL Integration Test
* Redis 기반 분산 환경 Idempotency 처리
* Refresh Token
* Role 기반 권한 관리
* Reward History Pagination
* Database Index 최적화
* 구조화된 Logging
* Metrics 및 Monitoring
* Docker 기반 애플리케이션 배포
* CI/CD Pipeline
* 동시 요청 Load Test
* Kafka 기반 Event-driven Architecture

---

# 19. 프로젝트에서 해결하고자 한 문제

이 프로젝트의 핵심은 단순한 CRUD API 구현이 아니라 **포인트 지급이라는 상태 변경 작업을 안정적으로 처리하는 것**입니다.

특히 다음 문제를 중심으로 설계했습니다.

```text id="g5m8x3"
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

각 계층의 동작 검증
    ↓
Unit / Repository / Integration Test
```

이를 통해 **Spring Boot 기반 REST API, JPA, PostgreSQL, JWT 인증, 트랜잭션 기반 비즈니스 로직, 동시성 제어, DB Migration, 테스트**까지 백엔드 서비스 개발의 주요 요소를 직접 구현하고 검증했습니다.
