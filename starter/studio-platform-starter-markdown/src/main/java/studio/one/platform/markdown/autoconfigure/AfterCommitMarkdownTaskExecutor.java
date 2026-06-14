package studio.one.platform.markdown.autoconfigure;

import java.util.Objects;
import java.util.concurrent.Executor;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import studio.one.platform.markdown.application.port.MarkdownTaskExecutor;

final class AfterCommitMarkdownTaskExecutor implements MarkdownTaskExecutor {
    private final Executor executor;

    AfterCommitMarkdownTaskExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void executeAfterCommit(Runnable task) {
        Runnable submit = () -> executor.execute(task);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
            return;
        }
        submit.run();
    }
}
