package com.prgate.projects.application.command;

/** The responsible teacher is derived from Actor, not supplied in this command. */
public record CreateCourseCommand(String name, String section, String academicPeriod) {
}
