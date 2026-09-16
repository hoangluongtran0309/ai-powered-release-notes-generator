package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitivePathRulesTest {

    // The shipped baseline in application.properties.
    private static final SensitivePathRules RULES = new SensitivePathRules(List.of(
            "**/security/**", "**/auth/**", "**/*Security*", "**/*Auth*", "**/*Credential*",
            "**/*Password*", "**/db/migration/**", "**/*.sql", "**/pom.xml", "**/package.json",
            "**/package-lock.json", ".github/workflows/**", "**/Dockerfile",
            "**/application*.properties", "**/*.env*"
    ));
    private static final SensitivePaths BASELINE = RULES.forProject(List.of());

    @ParameterizedTest
    @ValueSource(strings = {
            "security/Keys.java",
            "src/main/java/com/acme/security/Filter.java",
            "auth/login.ts",
            "src/main/java/com/acme/SecurityConfiguration.java",
            "src/OAuthClient.java",
            "src/CredentialCipher.java",
            "src/PasswordValidator.java",
            "src/main/resources/db/migration/V9__roles.sql",
            "scripts/seed.sql",
            "pom.xml",
            "modules/api/pom.xml",
            "package.json",
            "web/package-lock.json",
            ".github/workflows/ci.yml",
            "Dockerfile",
            "deploy/Dockerfile",
            "src/main/resources/application.properties",
            "src/main/resources/application-prod.properties",
            ".env.example"
    })
    void baselineMatchesSensitivePathsAtAnyDepth(String path) {
        assertThat(BASELINE.matches(List.of(modified(path)))).containsExactly(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/com/acme/ReleaseService.java",
            "README.md",
            "docs/guide.md",
            "src/main/resources/templates/changes.html",
            "workflows/ci.yml"
    })
    void baselineIgnoresOrdinaryFiles(String path) {
        assertThat(BASELINE.matches(List.of(modified(path)))).isEmpty();
    }

    @Test
    void renameChecksThePreviousPathToo() {
        ChangedFile movedOut = new ChangedFile(
                "src/main/java/Filter.java",
                "src/main/java/security/Filter.java",
                ChangedFileKind.RENAMED
        );

        assertThat(BASELINE.matches(List.of(movedOut))).containsExactly("src/main/java/security/Filter.java");
    }

    @Test
    void reportsEachMatchOnceInReportedOrder() {
        assertThat(BASELINE.matches(List.of(
                modified("pom.xml"),
                modified("src/App.java"),
                modified("db/migration/V2__x.sql"),
                modified("pom.xml")
        ))).containsExactly("pom.xml", "db/migration/V2__x.sql");
    }

    @Test
    void anUnparseablePathIsTreatedAsSensitive() {
        String unparseable = "src/bad\0name.java";

        assertThat(BASELINE.matches(List.of(modified(unparseable)))).containsExactly(unparseable);
    }

    @Test
    void aProjectAddsPatternsAfterTheBaselineWithoutRepeats() {
        List<String> effective = RULES.effective(List.of("**/billing/**", "pom.xml", "**/pom.xml"));

        assertThat(effective).startsWith(RULES.baseline().toArray(String[]::new));
        assertThat(effective.subList(RULES.baseline().size(), effective.size()))
                .containsExactly("**/billing/**", "pom.xml");
        assertThat(RULES.effective(List.of())).isEqualTo(RULES.baseline());
    }

    @ParameterizedTest
    @ValueSource(strings = {"billing/Invoice.java", "src/main/java/com/acme/billing/tax/Rate.java"})
    void anAddedPatternMatchesAtAnyDepth(String path) {
        SensitivePaths project = RULES.forProject(List.of("**/billing/**"));

        assertThat(project.matches(List.of(modified(path)))).containsExactly(path);
        assertThat(BASELINE.matches(List.of(modified(path)))).isEmpty();
    }

    @Test
    void additionsNeverRemoveTheBaseline() {
        SensitivePaths project = RULES.forProject(List.of("**/billing/**"));

        assertThat(project.matches(List.of(modified("pom.xml"), modified("billing/Invoice.java"))))
                .containsExactly("pom.xml", "billing/Invoice.java");
    }

    @Test
    void rejectsAnInvalidPattern() {
        assertThatThrownBy(() -> new SensitivePathRules(List.of("**/[unclosed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid pattern");
    }

    @Test
    void rejectsAnEmptyList() {
        assertThatThrownBy(() -> new SensitivePathRules(Arrays.asList("", "  ", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one pattern");
    }

    private static ChangedFile modified(String path) {
        return new ChangedFile(path, null, ChangedFileKind.MODIFIED);
    }
}
