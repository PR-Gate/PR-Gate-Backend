package com.prgate.ai.domain.model.valueobjects;
import java.util.Map;
import com.prgate.ai.domain.model.enums.CheckMechanism;
public record TechnicalCheck(CriterionId criterionId,CheckMechanism mechanism,String target,Map<String,String> parameters,String rationale) {
 public TechnicalCheck {
  Checks.present(criterionId); Checks.present(mechanism); target=Checks.text(target);
  parameters=Map.copyOf(Checks.present(parameters));
  parameters.forEach((k,v)-> { Checks.text(k); Checks.text(v); });
  rationale=Checks.text(rationale);
  Checks.require(mechanism!=CheckMechanism.MANUAL_REVIEW || parameters.isEmpty(),"manual review has no analyzer parameters");
 }
}