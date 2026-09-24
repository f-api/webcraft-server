# WebCraft Server (Spring 배포판)

WebCraft를 실제로 띄워 플레이할 수 있는 Spring Boot 서버입니다. 게임 내부(월드·틱·프로토콜·웹 클라이언트)는
엔진 JAR([f-api/webcraft-engine](https://github.com/f-api/webcraft-engine) `v2.3.0-server`)이 제공하고,
이 앱은 REST·WebSocket·저장 계층을 담당합니다. 빌드하면 웹 클라이언트까지 들어간 JAR 하나가 나옵니다.

## 1. 빌드

JDK 21이 필요합니다. 엔진은 첫 빌드 때 GitHub 릴리스에서 받고 체크섬을 확인합니다.

```bash
git clone https://github.com/f-api/webcraft-server.git
cd webcraft-server
./gradlew bootJar
# → build/libs/game-expert-0.0.1-SNAPSHOT.jar
```

## 2. 실행

MySQL 8과 Redis 7이 필요합니다.

### Docker로 한 번에 (서버에 올릴 때 권장)

```bash
cp docker/env.example docker/.env      # 필요하면 값 수정
docker compose -f docker/compose.yml --env-file docker/.env up -d
```

`http://<서버 주소>:8080/` 에 접속해 닉네임을 넣고 월드를 만들면 바로 플레이할 수 있습니다.
JAR만 서버에 올렸다면 `GAME_JAR=/경로/game.jar` 로 위치를 알려 주세요.

### JAR 직접 실행

```bash
java -jar build/libs/game-expert-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mysql://<DB 호스트>:3306/webcraft \
  --spring.datasource.password=<비밀번호> \
  --spring.data.redis.host=<Redis 호스트>
```

기본값(`src/main/resources/application.properties`)은 `localhost` 의 MySQL(`root`/`12345678`, DB `webcraft`)과 Redis입니다.
DB 스키마는 첫 기동 때 자동으로 만들어집니다.

## 3. 운영 스위치

환경 변수(또는 `--webcraft.…` 인자)로 켭니다. `docker/env.example` 에 같은 이름이 있습니다.

| 환경 변수 | 기본 | 설명 |
|---|---|---|
| `WEBCRAFT_PLAYERS_MAX` | 3 | 동시 접속 상한. 넘으면 로비에 "정원이 찼습니다" 안내 |
| `WEBCRAFT_WORLDS_FIXED` | false | true면 월드 만들기·지우기를 막고 로비에서 버튼을 숨김. 월드를 하나 만든 뒤 켭니다 |
| `WEBCRAFT_WARMWORLDSONSTARTUP` | false | 기동 시 스폰 주변을 미리 올려 첫 입장을 빠르게 함 |
| `WEBCRAFT_WARMWORLDRADIUS` | 4 | 위 워밍 반경(청크) |
| `WEBCRAFT_KEEPWORLDSLOADED` | false | 모두 나가도 월드를 내리지 않아 재입장이 빠름 |
| `WEBCRAFT_PREGENERATERADIUS` | 0 | 아무도 없을 때 스폰 주변을 이 반경(청크)까지 미리 생성 |

## 서버 사양

3명 기준 2코어·4GB(AWS t4g.medium 급)에서 확인했습니다. 메모리가 작은 서버에서는 JVM이 힙을 스스로 맞춰 다시 뜹니다.
