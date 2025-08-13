package mrynarzewski.githubreviewer.demo.dto;

import java.util.List;

public record RepoResponse(
		String repositoryName,
        String ownerLogin,
        List<BranchResponse> branches
   ) {

}
