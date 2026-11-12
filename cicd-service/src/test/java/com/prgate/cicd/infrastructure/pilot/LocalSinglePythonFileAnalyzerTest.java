package com.prgate.cicd.infrastructure.pilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.prgate.cicd.infrastructure.sonar.SonarProjectHttpAdapter;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LocalSinglePythonFileAnalyzerTest {
    @Test
    void launchesNativeScannerAndReadsTaskReport(@TempDir Path temporary) throws Exception {
        Path home = Files.createDirectories(temporary.resolve("scanner with spaces"));
        Path bin = Files.createDirectories(home.resolve("bin"));
        boolean windows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        Path script = bin.resolve(windows ? "sonar-scanner.bat" : "sonar-scanner");
        Files.writeString(script, windows
                ? "@echo off\r\nif not exist submission.py exit /b 1\r\nmkdir .scannerwork\r\necho ceTaskId=task-123>.scannerwork\\report-task.txt\r\nexit /b 0\r\n"
                : "#!/bin/sh\nset -eu\ntest -f submission.py\nmkdir .scannerwork\nprintf 'ceTaskId=task-123\\n' > .scannerwork/report-task.txt\n");
        if (!windows) { script.toFile().setExecutable(true); }
        var sonar = mock(SonarProjectHttpAdapter.class);
        when(sonar.awaitPilotAnalysis(eq("task-123"), anyString()))
                .thenReturn(new SonarProjectHttpAdapter.PilotAnalysis("analysis-123", "OK"));
        var analyzer = new LocalSinglePythonFileAnalyzer(sonar, "http://localhost:9000", "test-token", home.toString());
        var result = analyzer.analyze("demo.py", "print(1)\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("analysis-123", result.analysisId());
        assertEquals("OK", result.qualityGateStatus());
        verify(sonar).ensurePilotProjectExists(result.projectKey());
    }

    @Test
    void acceptsAStandalonePythonScript() {
        assertDoesNotThrow(() -> LocalSinglePythonFileAnalyzer.validate(
                "demo.py", "print('hola')\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsPathsBinaryFilesAndOversizedUploads() {
        byte[] script = "print('hola')".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> LocalSinglePythonFileAnalyzer.validate("../demo.py", script));
        assertThrows(IllegalArgumentException.class,
                () -> LocalSinglePythonFileAnalyzer.validate("demo.py", new byte[]{0, 1}));
        assertThrows(IllegalArgumentException.class,
                () -> LocalSinglePythonFileAnalyzer.validate("demo.py", new byte[256 * 1024 + 1]));
    }
}
