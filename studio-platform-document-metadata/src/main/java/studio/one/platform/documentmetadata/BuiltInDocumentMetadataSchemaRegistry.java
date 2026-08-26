package studio.one.platform.documentmetadata;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.documentmetadata.DocumentMetadataFieldDescriptor.ValueType;

public final class BuiltInDocumentMetadataSchemaRegistry implements DocumentMetadataSchemaRegistry {

    public static final String VERSION = "2026.08.1";
    private final List<DocumentMetadataSchema> schemas;

    public BuiltInDocumentMetadataSchemaRegistry() {
        Map<DocumentSemanticType, List<DocumentMetadataFieldDescriptor>> fields = new EnumMap<>(
                DocumentSemanticType.class);
        for (DocumentSemanticType type : DocumentSemanticType.values()) {
            fields.put(type, new ArrayList<>(commonFields()));
        }
        add(fields, DocumentSemanticType.BOOK,
                field("authors", "저자", true, true, true, ValueType.TEXT),
                field("editors", "편집자", false, false, true, ValueType.TEXT),
                field("translators", "번역자", false, false, true, ValueType.TEXT),
                field("publisher", "출판사", false, true, false, ValueType.TEXT),
                field("publicationDate", "발간일", false, true, false, ValueType.PARTIAL_DATE),
                field("edition", "판", false, false, false, ValueType.TEXT),
                field("isbn", "ISBN", false, true, true, ValueType.IDENTIFIER),
                field("subjects", "주제", false, false, true, ValueType.TEXT));
        add(fields, DocumentSemanticType.ACADEMIC_PAPER,
                field("authors", "저자", true, true, true, ValueType.TEXT),
                field("affiliations", "소속", false, true, true, ValueType.TEXT),
                field("abstract", "초록", false, true, false, ValueType.LONG_TEXT),
                field("doi", "DOI", false, true, false, ValueType.IDENTIFIER),
                field("journal", "학술지", false, false, false, ValueType.TEXT),
                field("conference", "학술대회", false, false, false, ValueType.TEXT),
                field("volume", "권", false, false, false, ValueType.TEXT),
                field("issue", "호", false, false, false, ValueType.TEXT),
                field("pages", "쪽", false, false, false, ValueType.TEXT),
                dateField("submittedDate", "제출일"),
                dateField("acceptedDate", "승인일"),
                dateField("publishedDate", "발간일"));
        add(fields, DocumentSemanticType.THESIS,
                field("authors", "저자", true, true, true, ValueType.TEXT),
                field("institution", "기관", false, true, false, ValueType.TEXT),
                field("department", "학과", false, true, false, ValueType.TEXT),
                field("degree", "학위", false, true, false, ValueType.TEXT),
                field("advisors", "지도교수", false, false, true, ValueType.TEXT),
                dateField("submittedDate", "제출일"),
                dateField("defenseDate", "심사일"),
                dateField("publishedDate", "발간일"));
        add(fields, DocumentSemanticType.REPORT,
                field("organization", "기관", false, true, false, ValueType.TEXT),
                field("reportNumber", "보고서 번호", false, false, false, ValueType.IDENTIFIER),
                field("version", "버전", false, false, false, ValueType.TEXT),
                field("executiveSummary", "요약", false, true, false, ValueType.LONG_TEXT),
                dateField("publicationDate", "발간일"));
        add(fields, DocumentSemanticType.POLICY,
                field("issuer", "발행기관", false, true, false, ValueType.TEXT),
                field("documentNumber", "문서 번호", false, false, false, ValueType.IDENTIFIER),
                field("jurisdiction", "관할", false, false, false, ValueType.TEXT),
                dateField("effectiveDate", "시행일"),
                dateField("revisedDate", "개정일"));
        add(fields, DocumentSemanticType.MANUAL,
                field("product", "제품/시스템", false, true, false, ValueType.TEXT),
                field("version", "버전", false, true, false, ValueType.TEXT),
                field("issuer", "발행기관", false, false, false, ValueType.TEXT),
                dateField("publishedDate", "발간일"),
                dateField("revisedDate", "개정일"));
        add(fields, DocumentSemanticType.PRESENTATION,
                field("presenters", "발표자", false, true, true, ValueType.TEXT),
                field("organization", "기관", false, false, false, ValueType.TEXT),
                field("event", "행사", false, false, false, ValueType.TEXT),
                dateField("presentationDate", "발표일"));
        add(fields, DocumentSemanticType.GENERAL,
                field("createdDate", "작성일", false, false, false, ValueType.PARTIAL_DATE),
                field("modifiedDate", "수정일", false, false, false, ValueType.PARTIAL_DATE),
                field("publishedDate", "발간일", false, false, false, ValueType.PARTIAL_DATE));

        schemas = List.of(
                schema(DocumentSemanticType.GENERAL, "일반 문서", "특정 유형으로 분류되지 않는 문서", fields),
                schema(DocumentSemanticType.BOOK, "도서", "저자·출판사·ISBN 중심의 서적", fields),
                schema(DocumentSemanticType.ACADEMIC_PAPER, "학술 논문", "초록·소속·DOI 중심의 논문", fields),
                schema(DocumentSemanticType.THESIS, "학위 논문", "기관·학위·지도교수 중심의 논문", fields),
                schema(DocumentSemanticType.REPORT, "보고서", "기관·보고서 번호·요약 중심의 보고서", fields),
                schema(DocumentSemanticType.POLICY, "정책/규정", "발행기관·시행일 중심의 정책 문서", fields),
                schema(DocumentSemanticType.MANUAL, "매뉴얼", "제품·버전 중심의 기술 문서", fields),
                schema(DocumentSemanticType.PRESENTATION, "프레젠테이션", "발표자·행사 중심의 발표 자료", fields),
                schema(DocumentSemanticType.UNKNOWN, "알 수 없음", "의미 유형을 판정하지 못한 문서", fields));
    }

    @Override
    public String schemaVersion() {
        return VERSION;
    }

    @Override
    public List<DocumentMetadataSchema> schemas() {
        return schemas;
    }

    private static List<DocumentMetadataFieldDescriptor> commonFields() {
        return List.of(
                field("title", "제목", true, true, false, ValueType.TEXT),
                field("subtitle", "부제", false, false, false, ValueType.TEXT),
                field("creators", "작성자", false, true, true, ValueType.TEXT),
                field("organizations", "기관/소속", false, false, true, ValueType.TEXT),
                field("language", "언어", false, false, false, ValueType.TEXT),
                field("keywords", "키워드", false, true, true, ValueType.TEXT),
                field("summary", "요약", false, true, false, ValueType.LONG_TEXT),
                field("identifiers", "식별자", false, false, true, ValueType.IDENTIFIER),
                field("dates", "관련 날짜", false, false, true, ValueType.PARTIAL_DATE));
    }

    private static DocumentMetadataSchema schema(DocumentSemanticType type, String name, String description,
            Map<DocumentSemanticType, List<DocumentMetadataFieldDescriptor>> fields) {
        return new DocumentMetadataSchema(type, name, description, List.copyOf(fields.get(type)));
    }

    private static void add(Map<DocumentSemanticType, List<DocumentMetadataFieldDescriptor>> fields,
            DocumentSemanticType type, DocumentMetadataFieldDescriptor... descriptors) {
        fields.get(type).addAll(List.of(descriptors));
    }

    private static DocumentMetadataFieldDescriptor dateField(String id, String label) {
        return field(id, label, false, true, false, ValueType.PARTIAL_DATE);
    }

    private static DocumentMetadataFieldDescriptor field(String id, String label, boolean required,
            boolean recommended, boolean multiValued, ValueType type) {
        return new DocumentMetadataFieldDescriptor(id, label, label + " 메타데이터", required, recommended,
                multiValued, type);
    }
}
