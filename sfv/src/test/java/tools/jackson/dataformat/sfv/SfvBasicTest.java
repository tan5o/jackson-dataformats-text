package tools.jackson.dataformat.sfv;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SfvBasicTest {
    @Test
    public void testParseItem() throws Exception {
        SfvFactory factory = SfvFactory.builder().defaultType(SfvType.ITEM).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode actual = mapper.readTree("?1;foo=2");
        JsonNode expected = mapper.readTree("[true, [[\"foo\", 2]]]");
        assertEquals(expected, actual);
    }

    @Test
    public void testWriteList() throws Exception {
        SfvFactory factory = SfvFactory.builder().defaultType(SfvType.LIST).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode input = mapper.readTree("[[\"hello\", []], [{\"__type\": \"token\", \"value\": \"abc\"}, []]]");
        String output = mapper.writeValueAsString(input);
        assertEquals("\"hello\",abc", output);
    }
}
