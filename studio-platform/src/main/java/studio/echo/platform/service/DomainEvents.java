package studio.echo.platform.service;

public interface DomainEvents {

    public void publish(Object event);

    public void publishAfterCommit(Object event);
    
}
