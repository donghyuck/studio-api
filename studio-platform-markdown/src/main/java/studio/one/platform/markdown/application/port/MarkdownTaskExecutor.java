package studio.one.platform.markdown.application.port;

@FunctionalInterface
public interface MarkdownTaskExecutor {

    void executeAfterCommit(Runnable task);

    static MarkdownTaskExecutor direct() {
        return Runnable::run;
    }
}
