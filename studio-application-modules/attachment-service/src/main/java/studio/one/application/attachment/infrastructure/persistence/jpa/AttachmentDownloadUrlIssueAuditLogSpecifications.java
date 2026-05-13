package studio.one.application.attachment.infrastructure.persistence.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;

final class AttachmentDownloadUrlIssueAuditLogSpecifications {

    private AttachmentDownloadUrlIssueAuditLogSpecifications() {
    }

    static Specification<AttachmentDownloadUrlIssueAuditLog> from(AttachmentDownloadUrlIssueAuditLogQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                setEquals("attachmentId", query.attachmentId(), predicates, root, cb);
                setEquals("objectType", query.objectType(), predicates, root, cb);
                setEquals("objectId", query.objectId(), predicates, root, cb);
                if (query.endpointKind() != null) {
                    setEquals("endpointKind", query.endpointKind().name(), predicates, root, cb);
                }
                setIssuedByPrincipalName(query.issuedByPrincipalName(), predicates, root, cb);
                if (query.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), query.from()));
                }
                if (query.to() != null) {
                    predicates.add(cb.lessThan(root.get("issuedAt"), query.to()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void setEquals(
            String field,
            Object value,
            List<Predicate> predicates,
            Root<AttachmentDownloadUrlIssueAuditLog> root,
            CriteriaBuilder cb) {
        if (value != null) {
            predicates.add(cb.equal(root.get(field), value));
        }
    }

    private static void setIssuedByPrincipalName(
            String issuedByPrincipalName,
            List<Predicate> predicates,
            Root<AttachmentDownloadUrlIssueAuditLog> root,
            CriteriaBuilder cb) {
        if (!StringUtils.hasText(issuedByPrincipalName)) {
            return;
        }
        String pattern = "%" + issuedByPrincipalName.toLowerCase(Locale.ROOT) + "%";
        predicates.add(cb.like(cb.lower(root.get("issuedByPrincipalName")), pattern));
    }
}
