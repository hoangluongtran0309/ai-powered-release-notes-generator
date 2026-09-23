package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepLTranslatorTest {

    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final DeepLTranslator TRANSLATOR = new DeepLTranslator(
            "deepl-test-key:fx", DEEPL.baseUrl(), Duration.ofSeconds(5), new ObjectMapper()
    );

    @AfterAll
    static void stopStub() {
        DEEPL.close();
    }

    @BeforeEach
    void resetStub() {
        DEEPL.reset();
    }

    @Test
    void sendsTheTextsWithTheKeyAndLanguageCodes() throws Exception {
        assertThat(TRANSLATOR.translate(List.of("Tables export as CSV.", "Users asked."), "en", "vi"))
                .containsExactly("[VI] Tables export as CSV.", "[VI] Users asked.");

        assertThat(DEEPL.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v2/translate");
            assertThat(request.authorization()).isEqualTo("DeepL-Auth-Key deepl-test-key:fx");
            assertThat(request.sourceLanguage()).isEqualTo("EN");
            assertThat(request.targetLanguage()).isEqualTo("VI");
        });
    }

    @ParameterizedTest
    @CsvSource({"en, EN-US", "en-GB, EN-GB", "pt, PT-BR", "pt-PT, PT-PT", "vi, VI", "vi-VN, VI-VN", "de, DE"})
    void mapsTargetLanguages(String tag, String code) {
        assertThat(DeepLTranslator.targetCode(tag)).isEqualTo(code);
    }

    @Test
    void sourceLanguagesDropTheirRegion() {
        assertThat(DeepLTranslator.sourceCode("en-GB")).isEqualTo("EN");
        assertThat(DeepLTranslator.sourceCode("vi")).isEqualTo("VI");
    }

    @Test
    void choosesTheFreeHostForAFreeKey() {
        assertThat(DeepLTranslator.defaultBaseUrl("abc:fx")).isEqualTo(DeepLTranslator.FREE_BASE_URL);
        assertThat(DeepLTranslator.defaultBaseUrl("abc")).isEqualTo(DeepLTranslator.PRO_BASE_URL);
    }

    @Test
    void splitsBatchesByCountAndSize() throws Exception {
        List<String> many = IntStream.range(0, 120).mapToObj(index -> "Text " + index).toList();
        assertThat(DeepLTranslator.batches(many)).extracting(List::size).containsExactly(50, 50, 20);

        String large = "x".repeat(50 * 1024);
        assertThat(DeepLTranslator.batches(List.of(large, large, large, "short")))
                .extracting(List::size).containsExactly(2, 2);

        assertThat(TRANSLATOR.translate(many, "en", "vi")).hasSize(120).last().isEqualTo("[VI] Text 119");
        assertThat(DEEPL.requests()).extracting(request -> request.texts().size()).containsExactly(50, 50, 20);
    }

    @Test
    void rateLimitsAndServerErrorsCanBeRetried() {
        DEEPL.fail(429, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.RATE_LIMITED);
                    assertThat(exception.retryable()).isTrue();
                });
        DEEPL.fail(503, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });
    }

    @Test
    void aRejectedKeyOrAnExhaustedQuotaIsNotRetried() {
        DEEPL.fail(403, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.REJECTED);
                    assertThat(exception.retryable()).isFalse();
                });
        DEEPL.fail(456, 1);
        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.QUOTA_EXCEEDED);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    @Test
    void aResponseWithTheWrongNumberOfTranslationsIsRejected() {
        DEEPL.dropOneTranslation();

        assertThatThrownBy(() -> TRANSLATOR.translate(List.of("a", "b"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.INVALID_RESPONSE);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    @Test
    void anUnreachableProviderCanBeRetried() {
        DeepLTranslator unreachable = new DeepLTranslator("key", "http://127.0.0.1:1", Duration.ofSeconds(1),
                new ObjectMapper());

        assertThatThrownBy(() -> unreachable.translate(List.of("a"), "en", "vi"))
                .isInstanceOfSatisfying(TranslationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(TranslationException.UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });
    }
}
