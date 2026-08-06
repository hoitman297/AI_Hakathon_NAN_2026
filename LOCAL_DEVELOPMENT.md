# 로컬 개발 시작하기

## 현재 구현된 화면

- 1990년대 한국 농촌 콘셉트 배경
- 8방향 남자 플레이어 캐릭터
- `WASD` / 방향키 이동과 카메라 추적
- 이동 중 감소하는 체력바
- 1280×720 기준 반응형 Phaser 게임 화면

현재 마을은 3200×2200 크기의 레이어형 월드입니다. 지형, 길, 강, 다리, 건물, 농장, 나무와 캐릭터가 각각 독립 오브젝트로 구성됩니다.

- 강은 걸어서 건널 수 없으며 세 곳의 다리로만 통과할 수 있습니다.
- 건물, 나무, 우물 등에는 충돌 영역이 있습니다.
- NPC와 주요 오브젝트를 클릭하면 조사 메시지가 표시됩니다.
- 양계장, 수박밭, 양계장 울타리는 클릭할 때 정상/사보타지 상태가 전환됩니다.

## 프론트엔드 실행

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:5173`을 엽니다.

같은 네트워크의 다른 기기에서도 확인하려면 다음 명령을 사용합니다.

```powershell
.\node_modules\.bin\vite.cmd --host=0.0.0.0
```

## 빌드 확인

```powershell
cd frontend
npm run lint
npm run build
```

## 데이터베이스와 Spring 서버

Docker Desktop을 먼저 실행한 뒤 루트의 `.env.example`을 `.env`로 복사하고 비밀번호를 변경합니다.

```powershell
Copy-Item .env.example .env
docker compose up -d
```

백엔드는 `.env` 값을 자동으로 읽지 않으므로 실행 터미널에 DB 접속 정보를 설정합니다.

```powershell
$env:DB_USERNAME='game_user'
$env:DB_PASSWORD='change_me'
cd backend
.\gradlew.bat bootRun
```

LLM 프록시는 별도 터미널에서 실행합니다.

```powershell
$env:DB_USERNAME='game_user'
$env:DB_PASSWORD='change_me'
cd llm-proxy
.\gradlew.bat bootRun
```

- 프론트엔드: `http://localhost:5173`
- 백엔드: `http://localhost:8080`
- LLM 프록시: `http://localhost:8081`

## 주요 소스 위치

- `frontend/src/game/MainScene.ts`: 배경, 플레이어, 이동, 체력 UI
- `frontend/src/game/config.ts`: Phaser 해상도와 렌더링 설정
- `frontend/src/App.tsx`: React 게임 페이지 구조
- `frontend/public/assets`: 게임 이미지 리소스
