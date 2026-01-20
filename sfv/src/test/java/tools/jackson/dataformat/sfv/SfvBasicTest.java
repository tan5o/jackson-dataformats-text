package tools.jackson.dataformat.sfv;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SfvBasicTest {
    @Test
    public void testParseItem() throws Exception {
        SfvFactory factory = SfvFactory.builder().defaultType(SfvType.ITEM).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode actual = mapper.readTree("?1;foo=2");
        JsonNode expected = new ObjectMapper().readTree("[true, [[\"foo\", 2]]]");
        assertEquals(expected, actual);
    }

    @Test
    public void testWriteList() throws Exception {
        SfvFactory factory = SfvFactory.builder().defaultType(SfvType.LIST).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode input = new ObjectMapper().readTree("[[\"hello\", []], [{\"__type\": \"token\", \"value\": \"abc\"}, []]]");
        String output = mapper.writeValueAsString(input);
        assertEquals("\"hello\", abc", output);
    }

    @Test
    public void testSfvMapper() throws Exception {
        // Test default SfvMapper
        SfvMapper mapper = new SfvMapper();
        assertNotNull(mapper);
        assertEquals("sfv", mapper.tokenStreamFactory().getFormatName());
    }

    @Test
    public void testSfvMapperShared() throws Exception {
        // Test shared instance
        SfvMapper shared = SfvMapper.shared();
        assertNotNull(shared);
        assertEquals("sfv", shared.tokenStreamFactory().getFormatName());
    }

    @Test
    public void testSfvMapperBuilder() throws Exception {
        // Test builder pattern
        SfvFactory factory = SfvFactory.builder().defaultType(SfvType.DICTIONARY).build();
        SfvMapper mapper = SfvMapper.builder(factory).build();
        assertNotNull(mapper);
    }
}
