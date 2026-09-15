package com.hoangluongtran0309.releaseflow.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One text an Organization already had translated between two languages. */
@Entity
@Table(name = "translation_cache")
@IdClass(TranslationCacheEntry.Key.class)
class TranslationCacheEntry {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Id
    @Column(name = "source_language", length = 16)
    private String sourceLanguage;

    @Id
    @Column(name = "target_language", length = 16)
    private String targetLanguage;

    @Id
    @Column(name = "text_hash", length = 64)
    private String textHash;

    @Column(name = "translated_text", nullable = false, columnDefinition = "text", updatable = false)
    private String translatedText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TranslationCacheEntry() {
    }

    TranslationCacheEntry(Key key, String translatedText, Instant createdAt) {
        this.organizationId = key.organizationId();
        this.sourceLanguage = key.sourceLanguage();
        this.targetLanguage = key.targetLanguage();
        this.textHash = key.textHash();
        this.translatedText = translatedText;
        this.createdAt = createdAt;
    }

    String getTextHash() {
        return textHash;
    }

    String getTranslatedText() {
        return translatedText;
    }

    record Key(UUID organizationId, String sourceLanguage, String targetLanguage, String textHash)
            implements Serializable {
    }
}
