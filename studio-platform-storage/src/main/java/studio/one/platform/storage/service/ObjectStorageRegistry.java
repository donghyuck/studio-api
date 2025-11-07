package studio.one.platform.storage.service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.storage.exception.ObjectStorageNotFoundException;

@Slf4j
public class ObjectStorageRegistry {

    /**
     * The name of the object storage registry service.
     */
    public static final String SERVICE_NAME = "services:cloud:objectstorage:registry";

    private final Map<String, CloudObjectStorage> map;

    public ObjectStorageRegistry(List<CloudObjectStorage> beans) {
        this.map = Collections.unmodifiableMap(
            beans.stream().collect(Collectors.toMap(
                s -> s.name().toLowerCase(Locale.ROOT), Function.identity()
            ))
        );
    }

    public CloudObjectStorage get(String id) {
        var s = map.get(id.toLowerCase(Locale.ROOT));
        if (s == null)
            throw new ObjectStorageNotFoundException(id, map.keySet());
        return s;
    }

    public Set<String> ids() {
        return map.keySet();
    }
}
