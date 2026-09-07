package studio.one.platform.team.application.command;

import studio.one.platform.team.application.error.TeamValidationException;

public record TeamAccessContext(Long userId, String username, boolean platformAdmin) {
    public Long requireUserId() {
        if (userId == null || userId <= 0) {
            throw new TeamValidationException("team actor userId is required");
        }
        return userId;
    }
}
