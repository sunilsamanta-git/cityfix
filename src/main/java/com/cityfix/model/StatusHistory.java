package com.cityfix.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Audit trail: one row for every change in a complaint's life. */
@Entity
public class StatusHistory {
    public StatusHistory() {}

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    private Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status toStatus;

    @Column(length = 500)
    private String remarks;

    private String changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Complaint getComplaint() { return complaint; }
    public void setComplaint(Complaint complaint) { this.complaint = complaint; }

    public Status getFromStatus() { return fromStatus; }
    public void setFromStatus(Status fromStatus) { this.fromStatus = fromStatus; }

    public Status getToStatus() { return toStatus; }
    public void setToStatus(Status toStatus) { this.toStatus = toStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
