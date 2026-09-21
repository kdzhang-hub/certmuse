package org.dromara.certmuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LocalStartupScriptTest {
    @Test
    void fullDockerStartupKeepsCurrentAppAvailableUntilVerifiedReplacementIsReady() throws Exception {
        Path backend = Path.of(System.getProperty("user.dir")).resolve("..").resolve("..").normalize();
        Path projectRoot = backend.resolve("..").normalize();
        String startScript = Files.readString(projectRoot.resolve("start.bat"));
        String verificationScript = Files.readString(projectRoot.resolve(
            Path.of("tools", "ops", "verify-local-startup.ps1")
        ));
        String backendDockerfile = Files.readString(projectRoot.resolve(
            Path.of("infra", "docker", "backend.Dockerfile")
        ));
        String frontendDockerfile = Files.readString(projectRoot.resolve(
            Path.of("infra", "docker", "frontend.Dockerfile")
        ));
        String nginxConfig = Files.readString(projectRoot.resolve(
            Path.of("infra", "docker", "nginx.conf")
        ));

        assertThat(startScript)
            .doesNotContain("docker compose stop backend frontend")
            .contains("git fetch origin develop")
            .contains("HEAD..origin/develop")
            .contains("get-local-source-revision.ps1")
            .contains("docker compose build --pull backend frontend")
            .doesNotContain("--no-cache")
            .doesNotContain("docker compose build --pull backend frontend postgres")
            .contains("docker compose up -d --force-recreate --wait --wait-timeout 180 backend frontend")
            .contains("verify-local-startup.ps1")
            .contains("-SourceRevision \"%SOURCE_REVISION%\"")
            .contains("-OpenBrowser");
        assertThat(verificationScript)
            .contains("Assert-ServiceUsesLatestImage 'backend'")
            .contains("Assert-ServiceUsesLatestImage 'frontend'")
            .contains("org.opencontainers.image.revision")
            .contains("$response.StatusCode -eq 200")
            .contains("?build=$SourceRevision")
            .contains("Start-Process $applicationUrl");
        assertThat(backendDockerfile).contains("org.opencontainers.image.revision=$CERTMUSE_SOURCE_REVISION");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.revision=$CERTMUSE_SOURCE_REVISION");
        assertThat(nginxConfig)
            .contains("location = /index.html")
            .contains("Cache-Control \"no-store, no-cache, must-revalidate\"");
    }
}
