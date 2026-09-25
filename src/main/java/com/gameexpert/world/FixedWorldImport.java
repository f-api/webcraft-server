package com.gameexpert.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * 고정 월드 서버는 새 월드를 만들지 않는다. DB에 월드가 없으면 스프링이 DB를 쓰기 전에 미리 생성해 둔
 * 월드(스폰 반경 16청크)를 받아 넣는다. 트리거는 넣지 않는다(RDS 는 만들 권한이 없고, 되는 DB 에서는
 * 엔진이 기동하며 직접 만든다). 끝 테이블 `worlds` 에 행이 있어야 끝난 것으로 보므로, 중간에 끊기면
 * 다음 기동 때 처음부터 다시 넣는다.
 */
public final class FixedWorldImport implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(FixedWorldImport.class);
    static final String DUMP_URL = "https://github.com/f-api/webcraft-server/releases/download/world-v1/world.sql.gz";
    static final String DUMP_SHA256 = "3f2b22bc19edb505fc80acca8b1f067555b55bfda478a6b51523bc3f65b6d1a5";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment environment = event.getEnvironment();
        if (!environment.getProperty("webcraft.worlds.fixed", Boolean.class, true)) return;
        String url = environment.getRequiredProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if (hasWorld(connection)) return;
            long started = System.nanoTime();
            log.info("미리 만든 고정 월드를 받는 중입니다");
            Path dump = download();
            try {
                log.info("미리 만든 고정 월드를 DB에 넣는 중입니다(처음 한 번, 몇 분 걸립니다)");
                int statements = load(connection, dump);
                log.info("고정 월드를 넣었습니다: 문장 {}개, {}초", statements, (System.nanoTime() - started) / 1_000_000_000L);
            } finally {
                Files.deleteIfExists(dump);
            }
        } catch (SQLException | IOException failure) {
            throw new IllegalStateException("미리 만든 고정 월드를 DB에 넣지 못했습니다", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("고정 월드를 받는 중에 중단되었습니다", interrupted);
        }
    }

    static boolean hasWorld(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "worlds", null)) {
            if (!tables.next()) return false;
        }
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM worlds")) {
            return rows.next() && rows.getLong(1) > 0;
        }
    }

    private static Path download() throws IOException, InterruptedException {
        Path file = Files.createTempFile("webcraft-world-", ".sql.gz");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(DUMP_URL)).build(),
                HttpResponse.BodyHandlers.ofFile(file));
        if (response.statusCode() != 200) throw new IOException("월드 데이터 응답 " + response.statusCode());
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), sha256())) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
            String actual = HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
            if (!DUMP_SHA256.equals(actual)) throw new IOException("월드 데이터 해시가 다릅니다: " + actual);
        }
        return file;
    }

    /** mysqldump 출력을 문장 단위로 실행한다. 트리거 블록(DELIMITER ;; … DELIMITER ;)은 건너뛴다. */
    static int load(Connection connection, Path dump) throws IOException, SQLException {
        int executed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(dump), 1 << 16), StandardCharsets.UTF_8), 1 << 20);
                Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            boolean trigger = false;
            for (String line; (line = reader.readLine()) != null; ) {
                if (trigger) {
                    if (line.startsWith("DELIMITER ;") && !line.startsWith("DELIMITER ;;")) trigger = false;
                    continue;
                }
                if (line.startsWith("DELIMITER ;;")) {
                    trigger = true;
                    continue;
                }
                if (sql.isEmpty() && (line.isBlank() || line.startsWith("--"))) continue;
                sql.append(line).append('\n');
                if (line.endsWith(";")) {
                    statement.execute(sql.toString());
                    sql.setLength(0);
                    executed++;
                }
            }
        }
        return executed;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
