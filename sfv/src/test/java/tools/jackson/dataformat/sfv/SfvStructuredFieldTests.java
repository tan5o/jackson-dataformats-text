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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test runner for the official HTTPWG structured-field-tests JSON files.
 * See: https://github.com/httpwg/structured-field-tests
 */
public class SfvStructuredFieldTests {
    
    // All known test files from the structured-field-tests repository
    private static final String[] TEST_FILES = {
            "structured-field-tests/binary.json",
            "structured-field-tests/boolean.json",
            "structured-field-tests/date.json",
            "structured-field-tests/dictionary.json",
            "structured-field-tests/display-string.json",
            "structured-field-tests/examples.json",
            "structured-field-tests/item.json",
            "structured-field-tests/key-generated.json",
            "structured-field-tests/large-generated.json",
            "structured-field-tests/list.json",
            "structured-field-tests/listlist.json",
            "structured-field-tests/number.json",
            "structured-field-tests/number-generated.json",
            "structured-field-tests/param-dict.json",
            "structured-field-tests/param-list.json",
            "structured-field-tests/param-listlist.json",
            "structured-field-tests/string.json",
            "structured-field-tests/string-generated.json",
            "structured-field-tests/token.json",
            "structured-field-tests/token-generated.json"
    };
    
    // Serialization test files
    private static final String[] SERIALISATION_TEST_FILES = {
            "structured-field-tests/serialisation-tests/key-generated.json",
            "structured-field-tests/serialisation-tests/number.json",
            "structured-field-tests/serialisation-tests/string-generated.json",
            "structured-field-tests/serialisation-tests/token-generated.json"
    };

    @Test
    public void runStructuredFieldTests() throws Exception {
        List<String> resources = listResources();
        Assumptions.assumeTrue(!resources.isEmpty(), "structured-field-tests resources not present");

        int totalTests = 0;
        int passedTests = 0;
        List<String> failures = new ArrayList<>();

        for (String resource : resources) {
            if (!resource.endsWith(".json")) {
                continue;
            }
            TestResult result = runTestFile(resource);
            totalTests += result.total;
            passedTests += result.passed;
            failures.addAll(result.failures);
        }

        System.out.println("Total tests: " + totalTests + ", Passed: " + passedTests + ", Failed: " + (totalTests - passedTests));
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
        }
        assertEquals(totalTests, passedTests, "Some tests failed: " + failures);
    }

    private static class TestResult {
        int total = 0;
        int passed = 0;
        List<String> failures = new ArrayList<>();
    }

    private TestResult runTestFile(String resource) throws Exception {
        TestResult result = new TestResult();
        ObjectMapper jsonMapper = new ObjectMapper();
        
        try (InputStream in = resourceStream(resource)) {
            if (in == null) {
                return result;
            }
            JsonNode root = jsonMapper.readTree(in);
            if (!root.isArray()) {
                return result;
            }
            for (JsonNode entry : root) {
                if (!entry.isObject()) {
                    continue;
                }
                result.total++;
                try {
                    runCase(resource, (ObjectNode) entry, jsonMapper);
                    result.passed++;
                } catch (AssertionError | Exception e) {
                    String name = entry.path("name").asText("<unnamed>");
                    result.failures.add(resource + " :: " + name + " :: " + e.getMessage());
                }
            }
        }
        return result;
    }

    private void runCase(String resource, ObjectNode entry, ObjectMapper jsonMapper) throws Exception {
        boolean mustFail = entry.path("must_fail").asBoolean(false);
        String name = entry.path("name").asText("<unnamed>");
        JsonNode rawNode = entry.get("raw");
        ArrayNode raws = (rawNode != null && rawNode.isArray()) ? (ArrayNode) rawNode : null;
        JsonNode expected = entry.get("expected");
        JsonNode canonicalNode = entry.get("canonical");

        SfvType type = resolveType(entry, resource);
        SfvFactory factory = SfvFactory.builder().defaultType(type).build();
        ObjectMapper mapper = new ObjectMapper(factory);

        if (raws != null) {
            String raw = joinRawValues(raws);
            if (mustFail) {
                assertThrows(Exception.class, () -> mapper.readTree(raw),
                        () -> resource + " :: " + name + " :: " + raw);
            } else {
                JsonNode actual = mapper.readTree(raw);
                if (expected != null) {
                    // Use semantic equality for numeric nodes
                    assertTrue(nodesEqual(expected, actual), 
                            () -> resource + " :: " + name + " :: " + raw + 
                            " ==> expected: " + expected + " but was: " + actual);
                }
            }
        }

        // Test canonical serialization: canonical is an array of strings
        if (!mustFail && expected != null && canonicalNode != null && canonicalNode.isArray()) {
            ArrayNode canonicalArray = (ArrayNode) canonicalNode;
            String canonicalStr = joinRawValues(canonicalArray);
            String output = mapper.writeValueAsString(expected);
            assertEquals(canonicalStr, output, () -> resource + " :: " + name + " :: canonical");
        }
    }

    /**
     * Compare JsonNodes with semantic equality for numbers.
     * Two numeric nodes are equal if their numeric values are equal.
     */
    private boolean nodesEqual(JsonNode a, JsonNode b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        if (a.isNumber() && b.isNumber()) {
            // Compare as BigDecimal for precision
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                if (!nodesEqual(a.get(i), b.get(i))) return false;
            }
            return true;
        }
        
        if (a.isObject() && b.isObject()) {
            ObjectNode oa = (ObjectNode) a;
            ObjectNode ob = (ObjectNode) b;
            java.util.Collection<String> fieldsA = oa.propertyNames();
            java.util.Collection<String> fieldsB = ob.propertyNames();
            if (fieldsA.size() != fieldsB.size()) return false;
            for (String field : fieldsA) {
                if (!nodesEqual(oa.get(field), ob.get(field))) return false;
            }
            return true;
        }
        
        return a.equals(b);
    }

    private SfvType resolveType(ObjectNode entry, String resource) {
        // Check for header_type field (official test format)
        JsonNode typeNode = entry.get("header_type");
        if (typeNode != null && typeNode.isTextual()) {
            return parseType(typeNode.asText());
        }
        // Fallback to type field
        typeNode = entry.get("type");
        if (typeNode != null && typeNode.isTextual()) {
            return parseType(typeNode.asText());
        }
        // Infer from filename
        if (resource.contains("dictionary") || resource.contains("param-dict")) {
            return SfvType.DICTIONARY;
        }
        if (resource.contains("item") || resource.contains("binary") || resource.contains("boolean")
                || resource.contains("date") || resource.contains("number") || resource.contains("string")
                || resource.contains("token") || resource.contains("display-string")) {
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

    private String joinRawValues(ArrayNode raws) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raws.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(raws.get(i).asText());
        }
        return sb.toString();
    }

    private List<String> listResources() throws IOException {
        List<String> results = new ArrayList<>();
        
        // Add main test files
        for (String candidate : TEST_FILES) {
            try (InputStream in = resourceStream(candidate)) {
                if (in != null) {
                    results.add(candidate);
                }
            }
        }
        
        // Add serialization test files
        for (String candidate : SERIALISATION_TEST_FILES) {
            try (InputStream in = resourceStream(candidate)) {
                if (in != null) {
                    results.add(candidate);
                }
            }
        }
        
        return results;
    }
}
