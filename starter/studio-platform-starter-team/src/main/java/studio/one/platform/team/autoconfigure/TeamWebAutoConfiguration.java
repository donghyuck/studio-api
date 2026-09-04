package studio.one.platform.team.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamMemberService;
import studio.one.platform.team.application.usecase.TeamJoinService;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.web.controller.TeamController;

@AutoConfiguration(after = TeamAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team.web", name = "enabled", havingValue = "true")
@ConditionalOnBean({ TeamService.class, TeamMemberService.class, TeamJoinService.class })
public class TeamWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TeamController teamController(
            TeamService teamService,
            TeamMemberService memberService,
            TeamJoinService joinService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new TeamController(teamService, memberService, joinService, principalResolverProvider);
    }

}
