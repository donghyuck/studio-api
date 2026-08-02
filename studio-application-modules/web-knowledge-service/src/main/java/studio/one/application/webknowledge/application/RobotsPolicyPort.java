package studio.one.application.webknowledge.application;

public interface RobotsPolicyPort {

    boolean isAllowed(String robotsText, String userAgent, String pathAndQuery);
}
