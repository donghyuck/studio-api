package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationJobDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;

public class RagRetrievalEvaluationJobService {
    private final RagRetrievalEvaluationRunner evaluationRunner;
    private final Executor executor;
    private final RagRetrievalEvaluationJobStore jobStore;

    public RagRetrievalEvaluationJobService(
            RagRetrievalEvaluationRunner evaluationRunner,
            Executor executor,
            RagRetrievalEvaluationJobStore jobStore) {
        this.evaluationRunner = evaluationRunner;
        this.executor = executor;
        this.jobStore = jobStore;
    }

    RagRetrievalEvaluationJobService(
            RagRetrievalEvaluationRunner evaluationRunner,
            Executor executor,
            RagRetrievalEvaluationJobStore jobStore,
            int ignoredMaxJobs) {
        this.evaluationRunner = evaluationRunner;
        this.executor = executor;
        this.jobStore = jobStore;
    }

    public RagRetrievalEvaluationJobDto submit(RagRetrievalEvaluationRequestDto request) {
        int questionCount = request.questions() == null ? 0 : request.questions().size();
        int strategyCount = request.strategies() == null ? 0 : request.strategies().size();
        JobState state = new JobState(
                "reval-job-" + UUID.randomUUID(),
                questionCount * Math.max(1, strategyCount),
                strategyCount,
                jobStore);
        state.persist();
        executor.execute(() -> run(state, request));
        return state.snapshot();
    }

    public Optional<RagRetrievalEvaluationJobDto> find(String jobId) {
        return jobStore.find(jobId);
    }

    public List<RagRetrievalEvaluationJobDto> list() {
        return jobStore.list();
    }

    private void run(JobState state, RagRetrievalEvaluationRequestDto request) {
        state.running();
        try {
            var response = evaluationRunner.evaluate(request, state);
            state.completed(response.runId());
        } catch (RuntimeException ex) {
            state.failed(ex.getMessage());
            throw ex;
        } catch (Error err) {
            state.failed(err.getMessage());
            throw err;
        }
    }

    private static final class JobState implements RagRetrievalEvaluationRunner.ProgressListener {
        private final String jobId;
        private final Instant createdAt = Instant.now();
        private final int totalQuestions;
        private final int totalStrategies;
        private final RagRetrievalEvaluationJobStore jobStore;
        private RagRetrievalEvaluationJobStatus status = RagRetrievalEvaluationJobStatus.PENDING;
        private Instant startedAt;
        private Instant completedAt;
        private int completedQuestions;
        private int completedStrategies;
        private String currentStrategy;
        private String currentQuestion;
        private String runId;
        private String errorMessage;

        private JobState(
                String jobId,
                int totalQuestions,
                int totalStrategies,
                RagRetrievalEvaluationJobStore jobStore) {
            this.jobId = jobId;
            this.totalQuestions = totalQuestions;
            this.totalStrategies = totalStrategies;
            this.jobStore = jobStore;
        }

        private synchronized void running() {
            status = RagRetrievalEvaluationJobStatus.RUNNING;
            startedAt = Instant.now();
            persist();
        }

        private synchronized void completed(String runId) {
            this.status = RagRetrievalEvaluationJobStatus.COMPLETED;
            this.completedAt = Instant.now();
            this.runId = runId;
            this.currentQuestion = null;
            this.currentStrategy = null;
            persist();
        }

        private synchronized void failed(String message) {
            this.status = RagRetrievalEvaluationJobStatus.FAILED;
            this.completedAt = Instant.now();
            this.errorMessage = message;
            persist();
        }

        @Override
        public synchronized void questionStarted(String strategy, String query) {
            this.currentStrategy = strategy;
            this.currentQuestion = query;
            persist();
        }

        @Override
        public synchronized void questionCompleted(String strategy, String query) {
            this.completedQuestions++;
            persist();
        }

        @Override
        public synchronized void strategyCompleted(String strategy) {
            this.completedStrategies++;
            persist();
        }

        private void persist() {
            jobStore.save(snapshot());
        }

        private synchronized RagRetrievalEvaluationJobDto snapshot() {
            return new RagRetrievalEvaluationJobDto(
                    jobId,
                    status.name(),
                    createdAt,
                    startedAt,
                    completedAt,
                    totalQuestions,
                    completedQuestions,
                    totalStrategies,
                    completedStrategies,
                    currentStrategy,
                    currentQuestion,
                    runId,
                    errorMessage);
        }
    }
}
