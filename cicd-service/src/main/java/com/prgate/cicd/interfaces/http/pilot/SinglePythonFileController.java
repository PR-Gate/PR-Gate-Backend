package com.prgate.cicd.interfaces.http.pilot;

import com.prgate.cicd.application.port.in.AnalyzeSinglePythonFileUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/pilot/file-analyses")
public class SinglePythonFileController {
    private final AnalyzeSinglePythonFileUseCase analyzeFile;

    public SinglePythonFileController(AnalyzeSinglePythonFileUseCase analyzeFile) {
        this.analyzeFile = analyzeFile;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail analysisError(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeSinglePythonFileUseCase.Result upload(@RequestParam("file") MultipartFile file) {
        try {
            return analyzeFile.analyze(file.getOriginalFilename(), file.getBytes());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read uploaded file", exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }
}
