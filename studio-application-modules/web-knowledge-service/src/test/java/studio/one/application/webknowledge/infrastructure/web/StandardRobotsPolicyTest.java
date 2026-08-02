package studio.one.application.webknowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandardRobotsPolicyTest {

    private final StandardRobotsPolicy policy = new StandardRobotsPolicy();

    @Test
    void usesMostSpecificAgentAndLongestMatchingRule() {
        String robots = """
                User-agent: *
                Disallow: /private/

                User-agent: studio-web-knowledge
                Disallow: /docs/*
                Allow: /docs/public/*
                Disallow: /download/*.zip$
                """;

        assertThat(policy.isAllowed(robots, "studio-web-knowledge/1.0", "/docs/public/start")).isTrue();
        assertThat(policy.isAllowed(robots, "studio-web-knowledge/1.0", "/docs/secret")).isFalse();
        assertThat(policy.isAllowed(robots, "studio-web-knowledge/1.0", "/download/a.zip")).isFalse();
        assertThat(policy.isAllowed(robots, "studio-web-knowledge/1.0", "/download/a.zip?raw=1")).isTrue();
        assertThat(policy.isAllowed(robots, "other-agent", "/private/key")).isFalse();
    }

    @Test
    void allowWinsWhenRulesHaveEqualSpecificity() {
        String robots = """
                User-agent: *
                Disallow: /same
                Allow: /same
                """;

        assertThat(policy.isAllowed(robots, "agent", "/same")).isTrue();
    }
}
