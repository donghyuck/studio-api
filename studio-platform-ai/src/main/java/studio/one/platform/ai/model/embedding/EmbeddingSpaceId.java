package studio.one.platform.ai.model.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class EmbeddingSpaceId {

    public static final String PREFIX = "es:v1:";

    private EmbeddingSpaceId() {
    }

    public static String from(EmbeddingSpaceContract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        return PREFIX + hex(sha256(canonicalJson(contract)));
    }

    public static String canonicalJson(EmbeddingSpaceContract contract) {
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        field(json, "contractVersion", contract.contractVersion()).append(',');
        field(json, "providerFamily", contract.providerFamily()).append(',');
        field(json, "apiModel", contract.apiModel()).append(',');
        json.append("\"dimension\":").append(contract.dimension()).append(',');
        field(json, "normalizationPolicy", contract.normalizationPolicy()).append(',');
        field(json, "indexTaskType", contract.indexTaskType()).append(',');
        field(json, "queryTaskType", contract.queryTaskType()).append(',');
        field(json, "inputTransformId", contract.inputTransformId()).append(',');
        field(json, "inputTransformVersion", contract.inputTransformVersion()).append(',');
        json.append("\"semanticOptions\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : contract.semanticOptions().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            string(json, entry.getKey()).append(':');
            value(json, entry.getValue());
        }
        return json.append("}}").toString();
    }

    private static StringBuilder field(StringBuilder target, String name, String value) {
        string(target, name).append(':');
        return value(target, value);
    }

    private static StringBuilder value(StringBuilder target, String value) {
        return value == null ? target.append("null") : string(target, value);
    }

    private static StringBuilder string(StringBuilder target, String value) {
        target.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                default -> {
                    if (current < 0x20) {
                        target.append(String.format("\\u%04x", (int) current));
                    } else {
                        target.append(current);
                    }
                }
            }
        }
        return target.append('"');
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(item & 0x0f, 16));
        }
        return result.toString();
    }
}
