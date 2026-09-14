package com.hoangluongtran0309.releaseflow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "output_language", nullable = false, length = OutputLanguage.MAX_LENGTH)
    private String outputLanguage;

    protected Organization() {
    }

    Organization(UUID id, String name, OutputLanguage outputLanguage, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.outputLanguage = outputLanguage.tag();
        this.createdAt = createdAt;
    }

    void changeOutputLanguage(OutputLanguage outputLanguage) {
        this.outputLanguage = outputLanguage.tag();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OutputLanguage getOutputLanguage() {
        return new OutputLanguage(outputLanguage);
    }
}
