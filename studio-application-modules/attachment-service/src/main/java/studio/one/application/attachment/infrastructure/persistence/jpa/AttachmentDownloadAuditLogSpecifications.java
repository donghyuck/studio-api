package studio.one.application.attachment.infrastructure.persistence.jpa;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import studio.one.application.attachment.domain.model.AttachmentDownloadAuditLog;
import studio.one.application.attachment.application.command.AttachmentDownloadAuditLogQuery;

final class AttachmentDownloadAuditLogSpecifications {

    private AttachmentDownloadAuditLogSpecifications() {
    }

    static Specification<AttachmentDownloadAuditLog> from(AttachmentDownloadAuditLogQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                setEquals("attachmentId", query.attachmentId(), predicates, root, cb);
                setEquals("objectType", query.objectType(), predicates, root, cb);
                setEquals("objectId", query.objectId(), predicates, root, cb);
                setEquals("tokenHash", query.tokenHash(), predicates, root, cb);
                if (query.result() != null) {
                    setEquals("result", query.result().name(), predicates, root, cb);
                }
                if (query.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), query.from()));
                }
                if (query.to() != null) {
                    predicates.add(cb.lessThan(root.get("requestedAt"), query.to()));
                }
                setEquals("clientIp", query.clientIp(), predicates, root, cb);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void setEquals(
            String field,
            Object value,
            List<Predicate> predicates,
            Root<AttachmentDownloadAuditLog> root,
            CriteriaBuilder cb) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        predicates.add(cb.equal(root.get(field), value));
    }
}
