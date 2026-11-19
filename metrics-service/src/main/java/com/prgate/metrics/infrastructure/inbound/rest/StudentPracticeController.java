package com.prgate.metrics.infrastructure.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.metrics.application.port.out.EvaluationStore;
import com.prgate.metrics.application.port.out.ProjectAccessPort;
import com.prgate.metrics.application.service.EvaluationAccessDenied;
import com.prgate.metrics.domain.model.enums.CheckStatus;
import com.prgate.metrics.infrastructure.outbound.ai.AiGradeEstimatorHttpAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/internal/v1/projects/{projectId}/practice")
public class StudentPracticeController {
    private final ProjectAccessPort access;
    private final EvaluationStore evaluations;
    private final AiGradeEstimatorHttpAdapter ai;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StudentPracticeController(ProjectAccessPort access, EvaluationStore evaluations,
            AiGradeEstimatorHttpAdapter ai, JdbcTemplate jdbc, ObjectMapper json) {
        this.access = access;
        this.evaluations = evaluations;
        this.ai = ai;
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Dashboard list(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId) {
        requireAccess(actor, projectId);
        var tests = jdbc.query("""
                SELECT * FROM metrics.student_practice_tests
                WHERE actor_id=? AND project_id=? ORDER BY created_at DESC, id
                """, row(), actor, projectId);
        var source = latest(projectId);
        boolean available = source.filter(value -> value.summary().criteria().stream()
                .anyMatch(criterion -> criterion.status() == CheckStatus.FAILED))
                .filter(value -> tests.stream().noneMatch(test -> test.evaluationId().equals(value.id()))).isPresent();
        return new Dashboard(available, tests.stream().map(this::view).toList());
    }

    @PostMapping
    public TestView generate(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId) {
        requireAccess(actor, projectId);
        var evaluation = latest(projectId).orElseThrow(NoSuchElementException::new);
        var existing = forEvaluation(actor, projectId, evaluation.id());
        if (existing.isPresent()) return view(existing.get());
        var rubric = evaluations.configuration(evaluation.configurationId()).orElseThrow().rubric();
        var findings = evaluation.summary().criteria().stream().filter(result -> result.status() == CheckStatus.FAILED)
                .limit(12).map(result -> {
                    var criterion = rubric.criteria().stream().filter(item -> item.id().equals(result.criterionId()))
                            .findFirst().orElseThrow();
                    String reasons = String.join("; ", result.checks().stream()
                            .filter(check -> check.status() == CheckStatus.FAILED).map(check -> check.reason()).toList());
                    String finding = criterion.description() + " — " + criterion.objective() + ". " + reasons;
                    return finding.substring(0, Math.min(finding.length(), 2000));
                }).toList();
        if (findings.isEmpty()) throw new IllegalArgumentException("The latest evaluation has no failed criteria");
        var questions = ai.practiceQuestions(findings);
        validateQuestions(questions);
        jdbc.update("""
                INSERT INTO metrics.student_practice_tests(id, actor_id, project_id, evaluation_id, questions)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT (actor_id, evaluation_id) DO NOTHING
                """, UUID.randomUUID(), actor, projectId, evaluation.id(), questions.toString());
        return view(forEvaluation(actor, projectId, evaluation.id()).orElseThrow());
    }

    @PostMapping("/{testId}/submit")
    public TestView submit(@RequestAttribute("verifiedActorId") UUID actor, @PathVariable UUID projectId,
            @PathVariable UUID testId, @Valid @RequestBody Submission submission) {
        requireAccess(actor, projectId);
        var test = byId(actor, projectId, testId);
        if (test.score() != null) return view(test);
        var answers = json.valueToTree(submission.answers());
        int correct = 0;
        for (int i = 0; i < test.questions().size(); i++) {
            int answer = submission.answers().get(i);
            if (answer < 0 || answer >= 4) throw new IllegalArgumentException("Answer outside option range");
            if (answer == test.questions().get(i).path("correctOption").asInt()) correct++;
        }
        jdbc.update("""
                UPDATE metrics.student_practice_tests
                SET answers=CAST(? AS jsonb), correct_count=?, submitted_at=CURRENT_TIMESTAMP
                WHERE id=? AND actor_id=? AND project_id=? AND submitted_at IS NULL
                """, answers.toString(), correct, testId, actor, projectId);
        return view(byId(actor, projectId, testId));
    }

    private Optional<EvaluationStore.StoredEvaluation> latest(UUID projectId) {
        var ids = jdbc.query("""
                SELECT evaluation_id FROM metrics.rubric_evaluations WHERE project_id=?
                ORDER BY evaluated_at DESC, evaluation_id DESC LIMIT 1
                """, (row, n) -> row.getObject(1, UUID.class), projectId);
        return ids.stream().findFirst().flatMap(evaluations::evaluation);
    }

    private Optional<StoredTest> forEvaluation(UUID actor, UUID projectId, UUID evaluationId) {
        return jdbc.query("""
                SELECT * FROM metrics.student_practice_tests WHERE actor_id=? AND project_id=? AND evaluation_id=?
                """, row(), actor, projectId, evaluationId).stream().findFirst();
    }

    private StoredTest byId(UUID actor, UUID projectId, UUID id) {
        return jdbc.query("""
                SELECT * FROM metrics.student_practice_tests WHERE actor_id=? AND project_id=? AND id=?
                """, row(), actor, projectId, id).stream().findFirst().orElseThrow(NoSuchElementException::new);
    }

    private RowMapper<StoredTest> row() {
        return (row, index) -> new StoredTest(row.getObject("id", UUID.class), row.getObject("evaluation_id", UUID.class),
                parse(row.getString("questions")), parse(row.getString("answers")), row.getObject("correct_count", Integer.class));
    }

    private JsonNode parse(String value) {
        try { return value == null ? null : json.readTree(value); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Invalid stored practice test", failure); }
    }

    private TestView view(StoredTest test) {
        var questions = new ArrayList<QuestionView>();
        for (int i = 0; i < test.questions().size(); i++) {
            var question = test.questions().get(i);
            var options = new ArrayList<String>();
            question.path("options").forEach(option -> options.add(option.asText()));
            boolean completed = test.score() != null;
            questions.add(new QuestionView(question.path("prompt").asText(), options,
                    completed ? test.answers().get(i).asInt() : null,
                    completed ? question.path("correctOption").asInt() : null,
                    completed ? question.path("explanation").asText() : null));
        }
        return new TestView(test.id(), test.score(), questions);
    }

    private static void validateQuestions(JsonNode questions) {
        if (!questions.isArray() || questions.size() != 3) throw new IllegalStateException("Invalid practice question count");
        var prompts = new HashSet<String>();
        for (var question : questions) {
            var options = question.path("options");
            var answer = question.path("correctOption");
            if (!validText(question.path("prompt")) || !prompts.add(question.path("prompt").asText().strip())
                    || !validText(question.path("explanation")) || !options.isArray() || options.size() != 4
                    || !answer.isIntegralNumber() || !answer.canConvertToInt() || answer.asInt() < 0 || answer.asInt() >= 4) {
                throw new IllegalStateException("Invalid practice question");
            }
            var distinct = new HashSet<String>();
            for (var option : options) {
                if (!validText(option) || !distinct.add(option.asText().strip())) {
                    throw new IllegalStateException("Invalid practice options");
                }
            }
        }
    }

    private static boolean validText(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 2000;
    }

    private void requireAccess(UUID actor, UUID projectId) {
        if (!access.check(projectId, actor).mayRead()) throw new EvaluationAccessDenied();
    }

    private record StoredTest(UUID id, UUID evaluationId, JsonNode questions, JsonNode answers, Integer score) { }
    public record Dashboard(boolean canGenerate, List<TestView> tests) { }
    public record TestView(UUID id, Integer correctCount, List<QuestionView> questions) { }
    public record QuestionView(String prompt, List<String> options, Integer selectedOption, Integer correctOption, String explanation) { }
    public record Submission(@NotNull @Size(min = 3, max = 3) List<@NotNull Integer> answers) { }
}
