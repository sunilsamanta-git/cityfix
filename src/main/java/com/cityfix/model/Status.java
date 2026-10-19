package com.cityfix.model;

import java.util.List;

/** Complaint lifecycle. The allowed transitions live here so the rule is in exactly one place. */
public enum Status {
    REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED, REJECTED;

    public List<Status> next() {
        return switch (this) {
            case REPORTED -> List.of(ASSIGNED, REJECTED);
            case ASSIGNED -> List.of(IN_PROGRESS, REJECTED);
            case IN_PROGRESS -> List.of(RESOLVED);
            case RESOLVED, REJECTED -> List.of();
        };
    }

    public boolean canMoveTo(Status target) { return next().contains(target); }
}
