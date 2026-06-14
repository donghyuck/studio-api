package studio.one.platform.markdown.application.port;

import java.util.function.Supplier;

@FunctionalInterface
public interface MarkdownTransactionOperations {

    <T> T required(Supplier<T> action);

    static MarkdownTransactionOperations direct() {
        return new MarkdownTransactionOperations() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
