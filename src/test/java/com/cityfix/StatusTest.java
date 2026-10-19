package com.cityfix;

import com.cityfix.model.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusTest {
    @Test
    void normalLifecycleIsAllowed() {
        assertTrue(Status.REPORTED.canMoveTo(Status.ASSIGNED));
        assertTrue(Status.ASSIGNED.canMoveTo(Status.IN_PROGRESS));
        assertTrue(Status.IN_PROGRESS.canMoveTo(Status.RESOLVED));
    }

    @Test
    void stepsCannotBeSkipped() {
        assertFalse(Status.REPORTED.canMoveTo(Status.RESOLVED));
        assertFalse(Status.ASSIGNED.canMoveTo(Status.RESOLVED));
    }

    @Test
    void finishedComplaintsAreClosed() {
        assertTrue(Status.RESOLVED.next().isEmpty());
        assertTrue(Status.REJECTED.next().isEmpty());
    }
}
