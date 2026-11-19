package com.prgate.users.interfaces.rest.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prgate.users.infraestructure.evaluation.EvaluationGateway;
import com.prgate.users.infraestructure.identity.KeycloakIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@ConditionalOnProperty(name="prgate.auth.provider",havingValue="keycloak")
@RequestMapping("/api/me/evaluations")
public class EvaluationGatewayController {
    private final KeycloakIdentityService identities;
    private final EvaluationGateway gateway;
    public EvaluationGatewayController(KeycloakIdentityService identities,EvaluationGateway gateway) {
        this.identities=identities; this.gateway=gateway;
    }
    @RequestMapping(value="/{*route}",method={RequestMethod.GET,RequestMethod.POST})
    public ResponseEntity<JsonNode> forward(@PathVariable String route,@RequestParam Map<String,String> parameters,
            @RequestBody(required=false) JsonNode body,HttpServletRequest request) {
        var actor=identities.current(request);
        var response=gateway.forward(actor.getId(),request.getMethod(),route,parameters,body);
        return ResponseEntity.status(response.status()).cacheControl(CacheControl.noStore()).body(response.body());
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> failure(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(),exception.getReason()));
    }
}
