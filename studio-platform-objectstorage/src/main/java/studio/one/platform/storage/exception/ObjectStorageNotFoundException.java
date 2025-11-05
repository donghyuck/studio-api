package studio.one.platform.storage.exception;
 
import java.util.Set;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.NotFoundException;

public class ObjectStorageNotFoundException extends NotFoundException {

    private static final ErrorType STORAGE_NOT_FOUND = ErrorType.of("error.storage.not.found.name", HttpStatus.NOT_FOUND);

    public ObjectStorageNotFoundException(String name, Set<String> avail) {
        super(STORAGE_NOT_FOUND, "ObjectStorage Not Found", avail);
    }

    public static ObjectStorageNotFoundException of(String name, Set<String> avail) {
        return new ObjectStorageNotFoundException(name, avail );
    }
}
