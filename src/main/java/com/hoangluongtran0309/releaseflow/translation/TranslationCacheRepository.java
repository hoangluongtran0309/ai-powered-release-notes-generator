package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface TranslationCacheRepository extends JpaRepository<TranslationCacheEntry, TranslationCacheEntry.Key> {

    List<TranslationCacheEntry> findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Collection<String> textHashes
    );
}
