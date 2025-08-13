package mrynarzewski.githubreviewer.demo.service;

import mrynarzewski.githubreviewer.demo.client.GithubClient;
import mrynarzewski.githubreviewer.demo.dto.BranchResponse;
import mrynarzewski.githubreviewer.demo.dto.RepoResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GitHubService {

    private final GithubClient client;

    public GitHubService(GithubClient client) {
        this.client = client;
    }

    public List<RepoResponse> listNonForkReposWithBranches(String username) {
        var repos = client.getUserRepos(username).stream()
                .filter(r -> !r.fork())
                .toList();

        return repos.stream().map(r -> {
            var branches = client.getRepoBranches(r.owner().login(), r.name())
                    .stream()
                    .map(b -> new BranchResponse(b.name(), b.commit().sha()))
                    .toList();
            return new RepoResponse(r.name(), r.owner().login(), branches);
        }).toList();
    }
}
