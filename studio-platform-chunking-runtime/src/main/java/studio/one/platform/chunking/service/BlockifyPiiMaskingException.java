package studio.one.platform.chunking.service;

public class BlockifyPiiMaskingException extends RuntimeException {

    private final boolean failPipeline;

    public BlockifyPiiMaskingException(String message, Throwable cause, boolean failPipeline) {
        super(message, cause);
        this.failPipeline = failPipeline;
    }

    public boolean failPipeline() {
        return failPipeline;
    }
}
