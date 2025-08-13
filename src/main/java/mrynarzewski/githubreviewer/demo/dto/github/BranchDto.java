package mrynarzewski.githubreviewer.demo.dto.github;

public record BranchDto(String name, Commit commit) {
    public record Commit(String sha) {}
}
