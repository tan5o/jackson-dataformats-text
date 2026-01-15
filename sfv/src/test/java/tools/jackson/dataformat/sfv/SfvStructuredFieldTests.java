package tools.jackson.dataformat.sfv;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SfvStructuredFieldTests {
    @Test
    public void runStructuredFieldTests() throws Exception {
        List<String> resources = listResources("structured-field-tests");
        Assumptions.assumeTrue(!resources.isEmpty(), "structured-field-tests resources not present");

        for (String resource : resources) {
            if (!resource.endsWith(".json")) {
                continue;
            }
            runTestFile(resource);
        }
    }

    private void runTestFile(String resource) throws Exception {
        ObjectMapper jsonMapper = new ObjectMapper();
        try (InputStream in = resourceStream(resource)) {
            if (in == null) {
                return;
            }
            JsonNode root = jsonMapper.readTree(in);
            if (!root.isArray()) {
                return;
            }
            for (JsonNode entry : root) {
                if (!entry.isObject()) {
                    continue;
                }
                runCase(resource, (ObjectNode) entry, jsonMapper);
            }
        }
    }

    private void runCase(String resource, ObjectNode entry, ObjectMapper jsonMapper) throws Exception {
        boolean mustFail = entry.path("must_fail").asBoolean(false);
        JsonNode rawNode = entry.get("raw");
        ArrayNode raws = (rawNode != null && rawNode.isArray()) ? (ArrayNode) rawNode : null;
        JsonNode expected = entry.get("expected");
        String canonical = entry.path("canonical").isTextual() ? entry.path("canonical").textValue() : null;

        SfvType type = resolveType(entry, resource);
        SfvFactory factory = SfvFactory.builder().defaultType(type).build();
        ObjectMapper mapper = new ObjectMapper(factory);

        if (raws != null) {
            for (JsonNode rawValue : raws) {
                String raw = rawValue.textValue();
                if (mustFail) {
                    assertThrows(Exception.class, () -> mapper.readTree(raw));
                } else {
                    JsonNode actual = mapper.readTree(raw);
                    if (expected != null) {
                        assertEquals(expected, actual);
                    }
                }
            }
        }

        if (!mustFail && expected != null && canonical != null) {
            String output = mapper.writeValueAsString(expected);
            assertEquals(canonical, output);
        }
    }

    private SfvType resolveType(ObjectNode entry, String resource) {
        JsonNode typeNode = entry.get("type");
        if (typeNode != null && typeNode.isTextual()) {
            return parseType(typeNode.textValue());
        }
        if (resource.contains("dictionary")) {
            return SfvType.DICTIONARY;
        }
        if (resource.contains("item")) {
            return SfvType.ITEM;
        }
        return SfvType.LIST;
    }

    private SfvType parseType(String value) {
        if ("dictionary".equalsIgnoreCase(value)) {
            return SfvType.DICTIONARY;
        }
        if ("item".equalsIgnoreCase(value)) {
            return SfvType.ITEM;
        }
        return SfvType.LIST;
    }

    private InputStream resourceStream(String resource) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
    }

    private List<String> listResources(String folder) throws IOException {
        List<String> results = new ArrayList<>();
        // Resource listing is not reliable from JARs, so try a common set.
        String[] common = new String[] {
                "structured-field-tests/dictionary.json",
                "structured-field-tests/list.json",
                "structured-field-tests/item.json",
                "structured-field-tests/serialisation-tests.json"
        };
        for (String candidate : common) {
            try (InputStream in = resourceStream(candidate)) {
                if (in != null) {
                    results.add(candidate);
                }
            }
        }
        return results;
    }
}
