package studio.one.platform.service;

/**
 * An interface for publishing domain events.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public interface DomainEvents {

    /**
     * Publishes a domain event immediately.
     *
     * @param event the event to publish
     */
    void publish(Object event);

    /**
     * Publishes a domain event after the current transaction has committed.
     *
     * @param event the event to publish
     */
    void publishAfterCommit(Object event);
    
}
