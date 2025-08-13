package mrynarzewski.githubreviewer.demo.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import mrynarzewski.githubreviewer.demo.dto.github.BranchDto;
import mrynarzewski.githubreviewer.demo.dto.github.RepoDto;
import mrynarzewski.githubreviewer.demo.exception.NotFoundException;

@Component
public class GithubClient {
    private final RestClient http;

    public GithubClient(RestClient.Builder builder,
                        @Value("${github.api.base}") String baseUrl,
                        @Value("${github.token:}") String token) {
        this.http = builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader(HttpHeaders.USER_AGENT, "repo-lister")
            .requestInterceptor((request, body, execution) -> {
                if (!token.isBlank()) {
                    request.getHeaders().setBearerAuth(token);
                }
                return execution.execute(request, body); // <-- to jest kluczowe
            })
            .build();
    }
    
    public List<RepoDto> getUserRepos(String username) {
    	try {
            return http.get()
                    .uri("/users/{username}/repos", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Not Found");
        }
    }
    
    public List<BranchDto> getRepoBranches(String owner, String repo) {
        return http.get()
                .uri("/repos/{owner}/{repo}/branches", owner, repo)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}