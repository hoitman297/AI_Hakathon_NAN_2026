# game-project (게임명 미정)

> 게임명 미정 — 추후 확정 시 프로젝트명/패키지명 변경 예정입니다. 현재는 임시로
> `game-project` 디렉토리명과 `com.gameproject` 패키지를 사용합니다.

지금 단계는 **개발 환경 세팅**만 완료된 상태입니다. 게임 로직, NPC 대화 로직,
LLM 연동, DB 엔티티 등 실제 기능은 아직 구현되어 있지 않습니다.

## 디렉토리 구조

```
game-project/
├── frontend/          # React + Phaser.js (Vite)
├── backend/           # Spring Boot API 서버
├── llm-proxy/         # LLM 프록시 서버 (Spring Boot)
├── docker-compose.yml # MySQL 로컬 실행용
├── .env.example
└── README.md
```

## 사용 버전

| 도구 | 버전 |
| --- | --- |
| Java | 21 (LTS) |
| Gradle | 9.5.1 (wrapper로 고정, 별도 설치 불필요) |
| Node.js | 24.15.0 (`frontend/.nvmrc` 참고) |
| MySQL | 8.0 (`mysql:8.0` 이미지 고정, latest 사용 금지) |
| Spring Boot | 4.1.0 |

## 포트

| 서비스 | 포트 |
| --- | --- |
| frontend (Vite dev server) | 5173 |
| backend | 8080 |
| llm-proxy | 8081 |
| MySQL | 3306 |

## 온보딩 절차

1. 저장소 클론

   ```bash
   git clone <repo-url>
   cd game-project
   ```

2. 루트 `.env.example` → `.env`로 복사 후 값 채우기 (docker-compose용 MySQL 계정)

   ```bash
   cp .env.example .env
   ```

3. `backend/application-local.yml.example`, `llm-proxy/application-local.yml.example`
   각각 복사해서 `application-local.yml`로 만들고 값 채우기 (2번에서 채운 `.env`의
   MySQL 계정/비밀번호와 맞춰주세요)

   ```bash
   cp backend/application-local.yml.example backend/application-local.yml
   cp llm-proxy/application-local.yml.example llm-proxy/application-local.yml
   ```

4. docker-compose로 MySQL 실행

   ```bash
   docker compose up -d
   ```

5. backend 실행

   ```bash
   cd backend && ./gradlew bootRun
   ```

6. llm-proxy 실행

   ```bash
   cd llm-proxy && ./gradlew bootRun
   ```

7. frontend 실행

   ```bash
   cd frontend && npm install && npm run dev
   ```

정상적으로 뜨면:
- `http://localhost:8080/health` → `OK`
- `http://localhost:8081/health` → `OK`
- `http://localhost:5173` → 브라우저에 "Hello Phaser" 텍스트가 보이는 Phaser 캔버스

## 브랜치 전략

<!-- 팀 컨벤션이 정해지면 여기에 작성 -->

## 참고

- `application-local.yml`, `.env`는 각자 로컬에만 존재하며 git에 커밋되지 않습니다
  (`.gitignore` 처리됨). 실제 값이 든 파일을 커밋하지 않도록 주의하세요.
- Gradle은 `gradlew`/`gradlew.bat` 래퍼를 사용하므로 별도 설치가 필요 없습니다.
