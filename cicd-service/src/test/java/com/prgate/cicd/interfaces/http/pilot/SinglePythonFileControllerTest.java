package com.prgate.cicd.interfaces.http.pilot;

import com.prgate.cicd.application.port.in.AnalyzeSinglePythonFileUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SinglePythonFileControllerTest {
    @Test
    void includesInfrastructureFailureDetailInResponse() throws Exception {
        AnalyzeSinglePythonFileUseCase useCase = (filename, content) -> {
            throw new IllegalStateException("SonarScanner CLI is unavailable");
        };
        var mvc = MockMvcBuilders.standaloneSetup(new SinglePythonFileController(useCase)).build();
        mvc.perform(multipart("/api/v1/pilot/file-analyses")
                        .file(new MockMultipartFile("file", "demo.py", "text/plain", "print(1)".getBytes())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("SonarScanner CLI is unavailable"));
    }

    @Test
    void receivesOnePythonFileAndReturnsSonarResult() throws Exception {
        byte[] source = "print('hola')\n".getBytes(StandardCharsets.UTF_8);
        AnalyzeSinglePythonFileUseCase useCase = (filename, content) -> {
            if (!"demo.py".equals(filename) || !java.util.Arrays.equals(source, content)) {
                throw new AssertionError("Uploaded file changed before analysis");
            }
            return new AnalyzeSinglePythonFileUseCase.Result("prgate-file-pilot-123", "analysis-123",
                    "OK", "http://localhost:9000/dashboard?id=prgate-file-pilot-123");
        };
        var mvc = MockMvcBuilders.standaloneSetup(new SinglePythonFileController(useCase)).build();

        mvc.perform(multipart("/api/v1/pilot/file-analyses")
                        .file(new MockMultipartFile("file", "demo.py", "text/x-python", source)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-123"))
                .andExpect(jsonPath("$.qualityGateStatus").value("OK"));
    }

    @Test
    void mapsInvalidFileToBadRequest() throws Exception {
        AnalyzeSinglePythonFileUseCase useCase = (filename, content) -> {
            throw new IllegalArgumentException("Only .py files are accepted");
        };
        var mvc = MockMvcBuilders.standaloneSetup(new SinglePythonFileController(useCase)).build();

        mvc.perform(multipart("/api/v1/pilot/file-analyses")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isBadRequest());
    }
}
