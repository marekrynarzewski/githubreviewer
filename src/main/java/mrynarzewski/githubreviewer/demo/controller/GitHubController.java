package mrynarzewski.githubreviewer.demo.controller;

import mrynarzewski.githubreviewer.demo.dto.RepoResponse;
import mrynarzewski.githubreviewer.demo.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/github")
public class GitHubController {

    private final GitHubService service;

    public GitHubController(GitHubService service) {
        this.service = service;
    }

    @GetMapping("/{username}/repos")
    public List<RepoResponse> list(@PathVariable String username) {
        return service.listNonForkReposWithBranches(username);
    }
}
