package mrynarzewski.githubreviewer.demo.integration;

import mrynarzewski.githubreviewer.demo.dto.BranchResponse;
import mrynarzewski.githubreviewer.demo.dto.RepoResponse;
import mrynarzewski.githubreviewer.demo.exception.ErrorPayload;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHubIntegrationTest {

    // Jeden serwer WireMock na klasę, z dynamicznym portem
    private static final WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wm.start();
        configureFor("localhost", wm.port());
    }

    @AfterAll
    static void stopWireMock() {
        wm.stop();
    }

    // Podmieniamy bazowy URL GitHuba na WireMocka w kontekście testowym
    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("github.api.base", wm::baseUrl);
        registry.add("github.token", () -> ""); // test bez tokena
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void happyPath_returnsNonForkReposWithBranches() {
        // given
        String user = "someuser";

        // /users/{user}/repos – zawiera 3 repo, jedno jest forkiem (powinno zostać odfiltrowane)
        wm.stubFor(get(urlEqualTo("/users/" + user + "/repos"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [
                      {"name":"alpha","owner":{"login":"someuser"},"fork":false},
                      {"name":"bravo","owner":{"login":"someuser"},"fork":true},
                      {"name":"charlie","owner":{"login":"someuser"},"fork":false}
                    ]
                """)));

        // /repos/{owner}/{repo}/branches – gałęzie i SHA dla nie-forków
        wm.stubFor(get(urlEqualTo("/repos/someuser/alpha/branches"))
            .willReturn(aResponse()
                .withHeader("Content-Type","application/json")
                .withBody("""
                    [
                      {"name":"main","commit":{"sha":"aaa111"}},
                      {"name":"dev","commit":{"sha":"bbb222"}}
                    ]
                """)));

        wm.stubFor(get(urlEqualTo("/repos/someuser/charlie/branches"))
            .willReturn(aResponse()
                .withHeader("Content-Type","application/json")
                .withBody("""
                    [
                      {"name":"main","commit":{"sha":"ccc333"}}
                    ]
                """)));

        // when
        ResponseEntity<RepoResponse[]> resp = rest.getForEntity(
            "http://localhost:" + port + "/github/" + user + "/repos", RepoResponse[].class);

        // then
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        RepoResponse[] body = resp.getBody();
        assertThat(body).isNotNull();
        // fork 'bravo' ma być odfiltrowany → zostały 2 repo
        assertThat(body.length).isEqualTo(2);

        Map<String, RepoResponse> byName = Arrays.stream(body)
            .collect(Collectors.toMap(RepoResponse::repositoryName, r -> r));

        // alpha
        RepoResponse alpha = byName.get("alpha");
        assertThat(alpha).isNotNull();
        assertThat(alpha.ownerLogin()).isEqualTo("someuser");
        assertThat(alpha.branches()).extracting(BranchResponse::name)
            .containsExactlyInAnyOrder("main", "dev");
        assertThat(alpha.branches()).extracting(BranchResponse::lastCommitSha)
            .containsExactlyInAnyOrder("aaa111", "bbb222");

        // charlie
        RepoResponse charlie = byName.get("charlie");
        assertThat(charlie).isNotNull();
        assertThat(charlie.branches()).hasSize(1);
        assertThat(charlie.branches().get(0).name()).isEqualTo("main");
        assertThat(charlie.branches().get(0).lastCommitSha()).isEqualTo("ccc333");

        // opcjonalnie: upewnij się, że NIE dzwoniliśmy po gałęzie dla forka 'bravo'
        wm.verify(0, getRequestedFor(urlEqualTo("/repos/someuser/bravo/branches")));
        
        // ============
        // 404 CASE
        // ============
        wm.resetAll(); // czyścimy stuby, żeby nie mieszać przypadków

        String missingUser = "user-does-not-exist";

        // GitHub dla nieistniejącego usera zwraca 404
        wm.stubFor(get(urlEqualTo("/users/" + missingUser + "/repos"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Not Found\"}")));

        ResponseEntity<ErrorPayload> notFound = rest.getForEntity(
            "http://localhost:" + port + "/github/" + missingUser + "/repos", ErrorPayload.class);

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().status()).isEqualTo(404);
        assertThat(notFound.getBody().message()).isEqualTo("Not Found");

        // upewnijmy się, że nie było żadnych zbędnych calli do /branches
        wm.verify(0, getRequestedFor(urlPathMatching("/repos/.+/.+/branches")));
    }
}
