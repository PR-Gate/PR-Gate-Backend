package com.prgate.cicd.interfaces.http;

import com.prgate.cicd.application.port.in.AuthorizedPipelineQueriesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineRunAuthorizationTest {
    @Test
    void anonymousRequestAndForgedUserHeaderAreRejectedBeforeQueries() throws Exception {
        var query = mock(AuthorizedPipelineQueriesUseCase.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PipelineRunController(query)).build();
        mvc.perform(get("/api/v1/pipeline-runs").param("repositoryId", "123")
                .header("X-User-Id", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pipeline-runs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(query);
    }

    @Test
    void unresolvedPrincipalIsForbidden() throws Exception {
        var query = mock(AuthorizedPipelineQueriesUseCase.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PipelineRunController(query)).build();
        mvc.perform(get("/api/v1/pipeline-runs").param("repositoryId", "123")
                .principal(() -> "unresolved-subject")).andExpect(status().isForbidden());
        verifyNoInteractions(query);
    }
}
