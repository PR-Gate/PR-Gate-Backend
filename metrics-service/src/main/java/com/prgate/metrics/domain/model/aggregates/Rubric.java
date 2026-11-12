package com.prgate.metrics.domain.model.aggregates;
import com.prgate.metrics.domain.model.entities.RubricCriterion;
import com.prgate.metrics.domain.model.enums.RubricStatus;
import com.prgate.metrics.domain.model.valueobjects.*;
import java.util.*;
public final class Rubric {
    private final RubricId id;
    private final RubricVersion version;
    private String title;
    private List<RubricCriterion> criteria;
    private RubricStatus status = RubricStatus.DRAFT;
    public Rubric(RubricId id, RubricVersion version, String title, List<RubricCriterion> criteria) {
        this.id = Checks.required(id); this.version = Checks.required(version);
        revise(title, criteria);
    }
    public void revise(String title, List<RubricCriterion> criteria) {
        Checks.require(status == RubricStatus.DRAFT, "published rubric cannot be edited");
        var newTitle = Checks.text(title);
        var copy = List.copyOf(Checks.required(criteria));
        Checks.require(copy.stream().map(RubricCriterion::id).distinct().count() == copy.size(),
                "criterion identifiers must be unique");
        this.title = newTitle; this.criteria = copy;
    }
    public PublishedRubric publish() {
        Checks.require(status == RubricStatus.DRAFT, "rubric is already published");
        var snapshot = new PublishedRubric(id, version, title, criteria);
        status = RubricStatus.PUBLISHED;
        return snapshot;
    }
    public Rubric nextVersion() {
        Checks.require(status == RubricStatus.PUBLISHED, "only a published rubric can be versioned");
        return new Rubric(id, version.next(), title, criteria);
    }
    public RubricId id() { return id; }
    public RubricVersion version() { return version; }
    public String title() { return title; }
    public List<RubricCriterion> criteria() { return criteria; }
    public RubricStatus status() { return status; }
}
