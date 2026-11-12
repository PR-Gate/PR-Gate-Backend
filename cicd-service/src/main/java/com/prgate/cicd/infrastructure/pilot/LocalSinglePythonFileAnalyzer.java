package com.prgate.cicd.infrastructure.pilot;

import com.prgate.cicd.application.port.in.AnalyzeSinglePythonFileUseCase;
import com.prgate.cicd.infrastructure.sonar.SonarProjectHttpAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class LocalSinglePythonFileAnalyzer implements AnalyzeSinglePythonFileUseCase {
    private static final int MAX_BYTES = 256 * 1024;
    private static final Duration SCANNER_TIMEOUT = Duration.ofMinutes(3);
    private final SonarProjectHttpAdapter sonar;
    private final String scannerSonarUrl;
    private final String token;
    private final String scannerHome;

    public LocalSinglePythonFileAnalyzer(SonarProjectHttpAdapter sonar,
            @Value("${app.sonar.base-url:http://localhost:9000}") String scannerSonarUrl,
            @Value("${app.sonar.token:}") String token,
            @Value("${app.file-pilot.scanner-home:}") String scannerHome) {
        this.sonar = sonar;
        this.scannerSonarUrl = scannerSonarUrl;
        this.token = token;
        this.scannerHome = scannerHome;
    }

    @Override
    public Result analyze(String filename, byte[] content) {
        validate(filename, content);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("SONAR_TOKEN is not configured");
        }
        Path scanner = scannerExecutable();
        String projectKey = "prgate-file-pilot-" + UUID.randomUUID().toString().replace("-", "");
        Path directory = null;
        try {
            directory = Files.createTempDirectory("prgate-file-pilot-");
            Files.write(directory.resolve("submission.py"), content);
            Files.writeString(directory.resolve("sonar-project.properties"),
                    "sonar.projectKey=" + projectKey + "\nsonar.sources=submission.py\nsonar.sourceEncoding=UTF-8\n");
            sonar.ensurePilotProjectExists(projectKey);
            runScanner(directory, scanner);
            Properties report = new Properties();
            try (var stream = Files.newInputStream(directory.resolve(".scannerwork").resolve("report-task.txt"))) {
                report.load(stream);
            }
            String taskId = report.getProperty("ceTaskId");
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalStateException("Scanner did not return a Sonar task ID");
            }
            var analysis = sonar.awaitPilotAnalysis(taskId, projectKey);
            return new Result(projectKey, analysis.analysisId(), analysis.qualityGateStatus(),
                    sonar.dashboardUrl(projectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot prepare or read the scanner workspace", exception);
        } finally {
            if (directory != null) {
                deleteWorkspace(directory);
            }
        }
    }

    private Path scannerExecutable() {
        if (scannerHome == null || scannerHome.isBlank()) {
            throw new IllegalStateException("Configura SONAR_SCANNER_HOME con la carpeta donde descomprimiste SonarScanner CLI.");
        }
        Path executable = Path.of(scannerHome).toAbsolutePath().resolve("bin")
                .resolve(isWindows() ? "sonar-scanner.bat" : "sonar-scanner");
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("No se encuentra SonarScanner CLI en SONAR_SCANNER_HOME/bin.");
        }
        return executable;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    private void runScanner(Path directory, Path scanner) throws IOException {
        // The script path is passed through the environment, never interpolated into shell code.
        List<String> command = isWindows()
                ? List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                        "& $env:PRGATE_SCANNER_SCRIPT; exit $LASTEXITCODE")
                : List.of(scanner.toString());
        var processBuilder = new ProcessBuilder(command);
        processBuilder.directory(directory.toFile());
        processBuilder.environment().put("PRGATE_SCANNER_SCRIPT", scanner.toString());
        processBuilder.environment().put("SONAR_HOST_URL", scannerSonarUrl);
        processBuilder.environment().put("SONAR_TOKEN", token);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(directory.resolve("scanner.log").toFile());
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo iniciar SonarScanner CLI; comprueba su instalación y permisos.", exception);
        }
        try {
            if (!process.waitFor(SCANNER_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("SonarScanner timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("SonarScanner failed (exit " + process.exitValue()
                        + "); comprueba SONAR_BASE_URL, SONAR_TOKEN y la instalación del scanner");
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SonarScanner interrupted", exception);
        }
    }

    static void validate(String filename, byte[] content) {
        if (filename == null || !filename.matches("(?i)[a-z0-9][a-z0-9._-]{0,99}\\.py")) {
            throw new IllegalArgumentException("Upload one Python .py file with a simple filename");
        }
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("File must contain between 1 byte and 256 KB");
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("File must be valid UTF-8 text", exception);
        }
        for (byte value : content) {
            if (value == 0) {
                throw new IllegalArgumentException("Binary content is not accepted");
            }
        }
    }

    private static void deleteWorkspace(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                if (!path.toAbsolutePath().normalize().startsWith(root)) {
                    throw new IllegalStateException("Unexpected scanner workspace path");
                }
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { path.toFile().deleteOnExit(); }
            });
        } catch (IOException exception) {
            directory.toFile().deleteOnExit();
        }
    }
}
