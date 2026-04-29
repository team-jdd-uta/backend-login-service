# backend-login-service

회원가입과 임시 로그인 API를 제공하는 Spring Boot 서비스입니다. 회원가입 시 source MariaDB의 `users`와 `outbox_event`를 같은 트랜잭션에 저장하고, Debezium이 `outbox_event`를 읽어 Kafka로 전달하는 CDC outbox 흐름의 시작점입니다.

## 역할

- `POST /register`로 회원가입을 처리합니다.
- 비밀번호를 BCrypt로 해시해 `users.password_hash`에 저장합니다.
- `UserRegistered` outbox event를 JSON payload로 저장합니다.
- `POST /login`은 현재 테스트용 stub입니다. 입력 username을 기준으로 성공 응답을 반환하며 실제 인증은 추후 구현 대상입니다.

## 기술 스택

- Java 21
- Spring Boot
- Spring Web
- Spring JDBC
- MariaDB
- BCrypt

## API

### 회원가입

```bash
curl -X POST http://localhost:8081/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'
```

성공 시 `201 Created`:

```json
{
  "success": true,
  "user": {
    "id": "<uuid>",
    "username": "alice"
  }
}
```

이미 존재하는 username이면 `409 Conflict`와 `success=false`를 반환합니다.

### 로그인

```bash
curl -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'
```

현재 로그인은 테스트용 stub입니다. DB 비밀번호 검증/JWT 발급은 아직 없습니다.

## Outbox Event

회원가입 성공 시 `outbox_event`에 저장되는 payload:

```json
{
  "eventId": "<uuid>",
  "userId": "<uuid>",
  "email": "alice",
  "name": "alice",
  "occurredAt": 1714000000000,
  "eventVersion": 1
}
```

Outbox row 주요 값:

| Column | 값 |
| --- | --- |
| `aggregate_type` | `user` |
| `aggregate_id` | 생성된 userId |
| `event_type` | `UserRegistered` |
| `status` | `PENDING` |
| `created_at` | epoch millis |

Debezium EventRouter 기준 topic은 `outbox.event.user`입니다.

## DB Schema

`src/main/resources/schema.sql`이 다음 테이블을 생성합니다.

- `users`
- `outbox_event`

Kubernetes에서는 source DB인 `login-mariadb`에 연결합니다.

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | HTTP 서버 포트 |
| `SPRING_DATASOURCE_URL` | `jdbc:mariadb://localhost:3308/app` | source MariaDB JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `app` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `app1234` | DB password |
| `SPRING_SQL_INIT_MODE` | `always` | schema.sql 실행 여부 |

## 로컬 실행

MariaDB가 먼저 떠 있어야 합니다.

```bash
cd loginservice
./gradlew bootRun
```

Docker 이미지 빌드:

```bash
cd loginservice
docker build -t team9-login-service:local .
```

## 운영 주의점

- 로그인 인증은 아직 실제 구현이 아닙니다.
- CDC 검증은 `register -> outbox_event row 생성 -> Debezium -> Kafka -> backend-user-service projection` 흐름으로 확인합니다.
- RDS 전환 시 Debezium user 권한과 binlog 설정은 DB parameter/group 및 grant 정책으로 별도 관리해야 합니다.
