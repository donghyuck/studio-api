package studio.one.platform.storage.service.impl;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.storage.autoconfigure.StorageProperties;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.ProviderCatalog;
import studio.one.platform.storage.web.dto.ProviderInfoDto;
import studio.one.platform.storage.web.dto.ProviderInfoDto.Capability;
import studio.one.platform.storage.web.dto.ProviderInfoDto.Health;

/**
 *
 * @author  donghyuck, son
 * @since 2025-11-09
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-09  donghyuck, son: 최초 생성.
 * </pre>
 */

@RequiredArgsConstructor
public class ProviderCatalogImpl implements ProviderCatalog {

    private final ObjectStorageRegistry registry;  
    private final StorageProperties props;  

    @Override
    public List<ProviderInfoDto> list(boolean includeHealth) {
        var ids = new ArrayList<>(registry.ids());  
        Collections.sort(ids);
        var conf = materializeProviders(props);  
        return ids.stream().map(id -> {
            var bean = registry.get(id);
            var p = conf.get(id);  
            var kind = detectType(bean);
            return ProviderInfoDto.builder()
                    .name(id)
                    .type(kind)
                    .enabled(p != null && p.isEnabled())
                    .status(p == null ? "unknown" : (p.isEnabled() ? "enabled" : "disabled"))
                    .health(includeHealth ? healthOf(bean) : Health.unknown)
                    .region(p != null ? p.getRegion() : null)
                    .endpointMasked(maskEndpoint(p != null ? resolveEndpoint(p) : null))
                    .ociNamespace(p != null && p.getOci() != null ? nullIfBlank(p.getOci().getNamespace()) : null)
                    .ociCompartmentMasked( maskTail(p != null && p.getOci() != null ? p.getOci().getCompartmentId() : null))
                    .fsRootMasked(maskFsRoot(p))
                    .s3PathStyle(p != null && p.getS3() != null ? p.getS3().getPathStyle() : null)
                    .s3PresignerEnabled(p != null && p.getS3() != null ? p.getS3().getPresignerEnabled() : null)
                    .capabilities(capabilitiesOf(kind))
                    .labels(null)  
                    .build();
        }).toList();
    }

    // ---------- helpers ----------
    private static Map<String, StorageProperties.Provider> materializeProviders(StorageProperties props) {
        var providers = props.getProviders();
        if (providers != null && !providers.isEmpty())
            return providers; 
        return Collections.emptyMap();
    }

    private static String detectType(Object bean) {
        if (bean instanceof S3ObjectStorage)
            return "s3";
        if (bean instanceof OciObjectStorage)
            return "oci";
        return "custom";
    }
 
    private static Health healthOf(Object bean) {
        return Health.ok;
    }

    private static List<Capability> capabilitiesOf(String kind) {
        switch (kind) {
            case "s3":
                return List.of(
                        Capability.OBJECT_GET,
                        Capability.OBJECT_PUT,
                        Capability.OBJECT_DELETE,
                        Capability.PRESIGNED_GET,
                        Capability.PRESIGNED_PUT,
                        Capability.HEAD_OBJECT,
                        Capability.LIST_OBJECTS,
                        Capability.LIST_BUCKETS);
            case "fs":
                return List.of(
                        Capability.OBJECT_GET,
                        Capability.OBJECT_PUT,
                        Capability.OBJECT_DELETE,
                        Capability.HEAD_OBJECT);
            default:
                return List.of();
        }
    }
 
    private static String resolveEndpoint(StorageProperties.Provider p) {
        var ep = p.getEndpoint();
        if (!StringUtils.hasText(ep))
            return null;
        if (ep.contains("{namespace}") && p.getOci() != null && StringUtils.hasText(p.getOci().getNamespace())) {
            ep = ep.replace("{namespace}", p.getOci().getNamespace());
        }
        return ep;
    }
 
    private static String maskEndpoint(String ep) {
        if (!StringUtils.hasText(ep))
            return null;
        try {
            var u = URI.create(ep);
            var host = Optional.ofNullable(u.getHost()).orElse(ep);
            return u.getScheme() + "://" + host + "/...";
        } catch (Exception ignored) {
            return ep;
        }
    }
 
    private static String maskTail(String s) {
        if (!StringUtils.hasText(s))
            return null;
        if (s.length() <= 8)
            return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
 
    private static String maskFsRoot(StorageProperties.Provider p) {
        if (p == null || p.getFs() == null || !StringUtils.hasText(p.getFs().getRoot()))
            return null;
        try {
            var root = Path.of(p.getFs().getRoot()).normalize().toString();
            int idx = Math.max(root.lastIndexOf('/'), root.lastIndexOf('\\'));
            return (idx > 0) ? "..." + root.substring(idx) : root;
        } catch (Exception ignored) {
            return "...";
        }
    }

    private static String nullIfBlank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

}
