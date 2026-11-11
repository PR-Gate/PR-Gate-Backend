package com.prgate.ai.domain.model.valueobjects;
public record TechnicalFinding(FindingId id,CriterionId criterionId,String ruleKey,String filePath,int line,String message) {
 public TechnicalFinding { Checks.present(id); Checks.present(criterionId); ruleKey=Checks.text(ruleKey); filePath=Checks.text(filePath); Checks.require(line>0,"line must be positive"); message=Checks.text(message); }
}