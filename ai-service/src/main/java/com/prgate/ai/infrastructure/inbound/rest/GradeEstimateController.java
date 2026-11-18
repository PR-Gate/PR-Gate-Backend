package com.prgate.ai.infrastructure.inbound.rest;
import com.prgate.ai.application.dto.*;
import com.prgate.ai.application.service.EstimateGradeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/grade-estimates")
public class GradeEstimateController {
    private final EstimateGradeService service;
    public GradeEstimateController(EstimateGradeService service) { this.service=service; }
    @PostMapping
    public GradeEstimateResult estimate(@RequestBody GradeEstimateRequest request) { return service.estimate(request); }
}
