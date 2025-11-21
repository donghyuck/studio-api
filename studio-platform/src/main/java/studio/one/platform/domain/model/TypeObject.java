package studio.one.platform.domain.model;

public interface TypeObject {
    
    public static final int UNKNOWN_OBJECT_TYPE = -1;
    public static final long UNKNOWN_OBJECT_ID = -1L;

    public abstract int getObjectType();

    public abstract long getObjectId();
}
