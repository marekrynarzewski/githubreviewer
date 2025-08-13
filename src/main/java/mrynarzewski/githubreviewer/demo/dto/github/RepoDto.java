package mrynarzewski.githubreviewer.demo.dto.github;

public record RepoDto(String name, Owner owner, boolean fork) {
    public record Owner(String login) {}
}
