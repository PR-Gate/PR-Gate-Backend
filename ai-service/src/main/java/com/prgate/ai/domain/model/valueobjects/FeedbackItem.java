package com.prgate.ai.domain.model.valueobjects;
import java.util.Set;
public record FeedbackItem(CriterionId criterionId,Set<FindingId> evidenceIds,String explanation,String suggestion) {
 public FeedbackItem { Checks.present(criterionId); evidenceIds=Set.copyOf(Checks.present(evidenceIds)); Checks.require(!evidenceIds.isEmpty(),"feedback item needs evidence"); explanation=Checks.text(explanation); suggestion=Checks.text(suggestion); }
}