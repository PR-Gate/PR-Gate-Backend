package com.prgate.ai.domain.model.valueobjects;
import java.util.Set;
/** Internal question with solution; never serialize directly to a student client. */
public record LearningQuestion(QuestionId id,CriterionId criterionId,String statement,String expectedAnswer,String gradingGuidance,Set<FindingId> evidenceIds) {
 public LearningQuestion {
  Checks.present(id); Checks.present(criterionId); statement=Checks.text(statement); expectedAnswer=Checks.text(expectedAnswer); gradingGuidance=Checks.text(gradingGuidance); evidenceIds=Set.copyOf(Checks.present(evidenceIds));
 }
}