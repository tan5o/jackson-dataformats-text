## Overview

[Jackson](/FasterXML/jackson) (Java) data format module that supports reading and writing
HTTP Structured Field Values (RFC 9651).

Support exists at streaming and databinding level, using the same JSON mapping
as the HTTPWG structured-field-tests JSON cases.
Binary values are represented as base32 strings in `{\"__type\":\"binary\",\"value\":\"...\"}`.

## Maven dependency

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-sfv</artifactId>
  <version>3.1.0</version>
</dependency>
```

## Usage

```java
SfvMapper mapper = new SfvMapper();
JsonNode list = mapper.readTree("\"hello\",?1");
String out = mapper.writeValueAsString(list);
```

To parse or generate a specific SFV top-level type (item, list, or dictionary),
set the factory default type:

```java
SfvFactory factory = SfvFactory.builder()
    .defaultType(SfvType.DICTIONARY)
    .build();
ObjectMapper mapper = new ObjectMapper(factory);
JsonNode dict = mapper.readTree("a=1, b=?0");
```

## Tests

The optional HTTPWG structured-field-tests JSON files can be placed under
`sfv/src/test/resources/structured-field-tests/`. If present, the
`SfvStructuredFieldTests` JUnit test will load and validate them.
