package studio.one.application.webknowledge.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import studio.one.application.webknowledge.application.RobotsPolicyPort;

public final class StandardRobotsPolicy implements RobotsPolicyPort {

    @Override
    public boolean isAllowed(String robotsText, String userAgent, String pathAndQuery) {
        if (robotsText == null || robotsText.isBlank()) {
            return true;
        }
        String agent = normalizeAgent(userAgent);
        List<Group> groups = parse(robotsText);
        int bestSpecificity = groups.stream()
                .filter(group -> group.matches(agent))
                .mapToInt(group -> group.specificity(agent))
                .max()
                .orElse(-1);
        if (bestSpecificity < 0) {
            return true;
        }
        String target = pathAndQuery == null || pathAndQuery.isBlank() ? "/" : pathAndQuery;
        Rule selected = groups.stream()
                .filter(group -> group.matches(agent) && group.specificity(agent) == bestSpecificity)
                .flatMap(group -> group.rules().stream())
                .filter(rule -> rule.matches(target))
                .max(java.util.Comparator
                        .comparingInt(Rule::specificity)
                        .thenComparing(Rule::allow))
                .orElse(null);
        return selected == null || selected.allow();
    }

    private static List<Group> parse(String source) {
        List<Group> groups = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        boolean rulesStarted = false;
        for (String rawLine : source.split("\\R")) {
            String line = rawLine.split("#", 2)[0].trim();
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if ("user-agent".equals(key)) {
                if (rulesStarted && !agents.isEmpty()) {
                    groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
                    agents.clear();
                    rules.clear();
                    rulesStarted = false;
                }
                if (!value.isBlank()) {
                    agents.add(value.toLowerCase(Locale.ROOT));
                }
            } else if (("allow".equals(key) || "disallow".equals(key)) && !agents.isEmpty()) {
                rulesStarted = true;
                if (!value.isBlank()) {
                    rules.add(new Rule("allow".equals(key), value));
                }
            }
        }
        if (!agents.isEmpty()) {
            groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
        }
        return List.copyOf(groups);
    }

    private static String normalizeAgent(String value) {
        if (value == null || value.isBlank()) {
            return "*";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('/');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    private record Group(List<String> agents, List<Rule> rules) {
        boolean matches(String requestedAgent) {
            return agents.stream().anyMatch(agent ->
                    "*".equals(agent) || requestedAgent.startsWith(agent) || agent.startsWith(requestedAgent));
        }

        int specificity(String requestedAgent) {
            return agents.stream()
                    .filter(agent -> "*".equals(agent)
                            || requestedAgent.startsWith(agent)
                            || agent.startsWith(requestedAgent))
                    .mapToInt(agent -> "*".equals(agent) ? 0 : agent.length())
                    .max()
                    .orElse(-1);
        }
    }

    private record Rule(boolean allow, String pattern) {
        boolean matches(String target) {
            return regex(pattern).matcher(target).find();
        }

        int specificity() {
            return pattern.replace("*", "").replace("$", "").length();
        }

        private static Pattern regex(String value) {
            boolean anchoredEnd = value.endsWith("$");
            String effective = anchoredEnd ? value.substring(0, value.length() - 1) : value;
            StringBuilder regex = new StringBuilder("^");
            for (int index = 0; index < effective.length(); index++) {
                char current = effective.charAt(index);
                if (current == '*') {
                    regex.append(".*");
                } else {
                    if ("\\.[]{}()+-^$|?".indexOf(current) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(current);
                }
            }
            if (anchoredEnd) {
                regex.append('$');
            }
            return Pattern.compile(regex.toString());
        }
    }
}
