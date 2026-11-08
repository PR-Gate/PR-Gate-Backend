package com.prgate.ai.infrastructure.inbound.rest;

import com.prgate.ai.application.dto.ApproveTechnicalPlanCommand;
import com.prgate.ai.application.port.in.*;
import com.prgate.ai.domain.model.valueobjects.PlanId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import static com.prgate.ai.infrastructure.inbound.rest.ApiResources.*;

@RestController
@RequestMapping("/api/technical-plans")
public final class TechnicalPlanController {
    private final TranslateRubricUseCase translate;
    private final GetTechnicalPlanUseCase get;
    private final ApproveTechnicalPlanUseCase approve;
    public TechnicalPlanController(TranslateRubricUseCase translate, GetTechnicalPlanUseCase get,
                                   ApproveTechnicalPlanUseCase approve) {
        this.translate = translate; this.get = get; this.approve = approve;
    }
    @PostMapping
    public ResponseEntity<PlanResource> create(@Valid @RequestBody CreatePlanRequest request) {
        var result = PlanResource.from(translate.translate(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/technical-plans/" + result.id())).body(result);
    }
    @GetMapping("/{id}")
    public PlanResource get(@PathVariable UUID id) { return PlanResource.from(get.get(new PlanId(id))); }
    @PostMapping("/{id}/approval")
    public PlanResource approve(@PathVariable UUID id) {
        return PlanResource.from(approve.approve(new ApproveTechnicalPlanCommand(new PlanId(id))));
    }
}
