package com.prgate.users.infraestructure.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Narrow authenticated facade; browser credentials and actor headers are never forwarded. */
@Component
public class EvaluationGateway {
    private static final String UUID_PATH="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String PROJECT="projects/"+UUID_PATH+"/";
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper json;
    private final String metricsUrl,metricsToken,projectsUrl,projectsToken;
    public EvaluationGateway(ObjectMapper json,@Value("${METRICS_BASE_URL:http://localhost:8084}") String metricsUrl,
            @Value("${METRICS_API_TOKEN:}") String metricsToken,@Value("${PROJECTS_BASE_URL:http://localhost:8083}") String projectsUrl,
            @Value("${METRICS_PROJECTS_TOKEN:}") String projectsToken) {
        this.json=json; this.metricsUrl=url(metricsUrl); this.metricsToken=metricsToken;
        this.projectsUrl=url(projectsUrl); this.projectsToken=projectsToken;
    }
    public Reply forward(UUID actor,String method,String route,Map<String,String> parameters,JsonNode body) {
        route=route.replaceFirst("^/","");
        boolean catalog=route.equals("catalog/courses") || route.matches("catalog/courses/"+UUID_PATH+"/projects");
        boolean allowed=method.equals("GET") && (catalog || route.matches(PROJECT+"(rubrics|repositories|analyses|configurations|evaluations)")
                || route.matches(PROJECT+"rubrics/"+UUID_PATH+"/versions/[1-9][0-9]{0,5}")
                || route.matches(PROJECT+"configurations/"+UUID_PATH+"(/quality-gate)?")
                || route.matches(PROJECT+"evaluations/"+UUID_PATH+"(/analysis|/grade-estimate)?"))
                || method.equals("POST") && (route.matches(PROJECT+"(rubrics|configurations|evaluations)")
                || route.matches(PROJECT+"rubrics/"+UUID_PATH+"/versions")
                || route.matches(PROJECT+"configurations/"+UUID_PATH+"/quality-gate/retry")
                || route.matches(PROJECT+"evaluations/"+UUID_PATH+"/(manual-reviews|grade-estimate|analysis/retry)"));
        boolean courseRubric = route.matches("courses/" + UUID_PATH + "/rubrics")
                || method.equals("POST") && route.matches("courses/" + UUID_PATH + "/rubrics/" + UUID_PATH + "/archive")
                || method.equals("POST") && route.matches("courses/" + UUID_PATH + "/rubrics/document-analysis")
                || method.equals("POST") && route.matches("courses/" + UUID_PATH + "/rubrics/" + UUID_PATH
                    + "/versions(/[1-9][0-9]{0,8}/technical-proposal)?");
        boolean practice = method.equals("GET") && route.matches(PROJECT + "practice")
                || method.equals("POST") && route.matches(PROJECT + "practice(/" + UUID_PATH + "/submit)?");
        if (!allowed && !courseRubric && !practice) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Evaluation route not available");
        if (!Set.of("page","size","repositoryId","gitRef").containsAll(parameters.keySet()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported evaluation query");
        String token=catalog?projectsToken:metricsToken;
        if (token.isBlank()) throw unavailable();
        StringBuilder query=new StringBuilder(catalog?"?userId="+actor:"");
        parameters.forEach((key,value)->query.append(query.isEmpty()?"?":"&").append(key).append("=")
                .append(URLEncoder.encode(value,StandardCharsets.UTF_8)));
        String payload=body==null?"{}":body.toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length>262144)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Evaluation request too large");
        try {
            String path=catalog?"/internal/v1/projects/"+route:"/internal/v1/"+route;
            var request=HttpRequest.newBuilder(URI.create((catalog?projectsUrl:metricsUrl)+path+query))
                    .timeout(Duration.ofSeconds(route.endsWith("/grade-estimate") ? 105
                            : route.endsWith("/technical-proposal") || route.endsWith("/document-analysis") || route.endsWith("/practice") ? 140 : 30))
                    .header("Authorization","Bearer "+token).header("Accept","application/json");
            if (!catalog) request.header("X-Actor-Id",actor.toString());
            if (method.equals("GET")) request.GET();
            else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            int status=response.statusCode();
            if (status>=200 && status<300)
                return new Reply(status,response.body().isBlank()?null:json.readTree(response.body()));
            // An internal credential failure is not a failure of the user's Keycloak session.
            if (status==401 || status>=500) throw unavailable();
            if (status==403) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No tienes acceso a la evaluación de este proyecto");
            if (status==404) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"El recurso de evaluación todavía no está disponible");
            if (status==409) throw new ResponseStatusException(HttpStatus.CONFLICT,"La configuración cambió; actualiza la página");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revisa los criterios, sus puntos y las condiciones de evaluación");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException exception) { throw unavailable(); }
    }
    private static String url(String value) {
        URI uri=URI.create(value);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !Set.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException("Invalid evaluation backend URL");
        return value.replaceAll("/+$","");
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El servicio de evaluación no está disponible");
    }
    public record Reply(int status,JsonNode body) { }
}
