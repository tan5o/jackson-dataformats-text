package tools.jackson.dataformat.sfv;

import java.io.*;
import java.nio.charset.StandardCharsets;

import tools.jackson.core.*;
import tools.jackson.core.base.TextualTSFactory;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.io.IOContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.TreeTraversingParser;

public final class SfvFactory extends TextualTSFactory {
    private static final long serialVersionUID = 1L;

    public static final String FORMAT_NAME_SFV = "sfv";

    static final int DEFAULT_SFV_PARSER_FEATURE_FLAGS = SfvReadFeature.collectDefaults();
    static final int DEFAULT_SFV_GENERATOR_FEATURE_FLAGS = SfvWriteFeature.collectDefaults();

    private final SfvType _defaultType;

    public SfvFactory() {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                DEFAULT_SFV_PARSER_FEATURE_FLAGS, DEFAULT_SFV_GENERATOR_FEATURE_FLAGS);
        _defaultType = SfvType.LIST;
    }

    SfvFactory(SfvFactory src) {
        super(src);
        _defaultType = src._defaultType;
    }

    SfvFactory(SfvFactoryBuilder b) {
        super(b);
        _defaultType = b.defaultType();
    }

    @Override
    public SfvFactoryBuilder rebuild() {
        return new SfvFactoryBuilder(this);
    }

    public static SfvFactoryBuilder builder() {
        return new SfvFactoryBuilder();
    }

    @Override
    public SfvFactory copy() {
        return new SfvFactory(this);
    }

    @Override
    public TokenStreamFactory snapshot() {
        return this;
    }

    protected Object readResolve() {
        return new SfvFactory(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean requiresPropertyOrdering() {
        return false;
    }

    @Override
    public boolean canUseCharArrays() {
        return false;
    }

    @Override
    public boolean canParseAsync() {
        return false;
    }

    @Override
    public String getFormatName() {
        return FORMAT_NAME_SFV;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false;
    }

    @Override
    public Class<? extends FormatFeature> getFormatReadFeatureType() {
        return SfvReadFeature.class;
    }

    @Override
    public Class<? extends FormatFeature> getFormatWriteFeatureType() {
        return SfvWriteFeature.class;
    }

    @Override
    public int getFormatReadFeatures() {
        return _formatReadFeatures;
    }

    @Override
    public int getFormatWriteFeatures() {
        return _formatWriteFeatures;
    }

    public boolean isEnabled(SfvReadFeature f) {
        return (_formatReadFeatures & f.getMask()) != 0;
    }

    public boolean isEnabled(SfvWriteFeature f) {
        return (_formatWriteFeatures & f.getMask()) != 0;
    }

    public SfvType defaultType() {
        return _defaultType;
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt, InputStream in) throws JacksonException {
        boolean autoClose = ctxt.isResourceManaged() || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE);
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        return _createParser(readCtxt, ctxt, autoClose ? new BufferedReader(reader) : reader);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt, Reader r) throws JacksonException {
        boolean autoClose = ctxt.isResourceManaged() || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE);
        try {
            JsonNode node = SfvParser.parse(readCtxt, ctxt, _formatReadFeatures, _defaultType, r);
            return new TreeTraversingParser(node, readCtxt);
        } catch (IOException e) {
            throw JacksonIOException.construct(e, r);
        } finally {
            if (autoClose) {
                try {
                    r.close();
                } catch (IOException e) {
                    throw JacksonIOException.construct(e, r);
                }
            }
            ctxt.close();
        }
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt, byte[] data, int offset, int len) throws JacksonException {
        return _createParser(readCtxt, ctxt,
                new InputStreamReader(new ByteArrayInputStream(data, offset, len), StandardCharsets.UTF_8));
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt, char[] data, int offset, int len, boolean recyclable) throws JacksonException {
        return _createParser(readCtxt, ctxt, new CharArrayReader(data, offset, len));
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ctxt, DataInput input) throws JacksonException {
        return _unsupported();
    }

    @Override
    protected JsonGenerator _createGenerator(ObjectWriteContext writeCtxt, IOContext ctxt, Writer out) throws JacksonException {
        int streamWriteFeatures = writeCtxt.getStreamWriteFeatures(_streamWriteFeatures);
        int formatWriteFeatures = writeCtxt.getFormatWriteFeatures(_formatWriteFeatures);
        return new SfvGenerator(writeCtxt, ctxt, streamWriteFeatures, formatWriteFeatures, out, _defaultType);
    }

    @Override
    protected JsonGenerator _createUTF8Generator(ObjectWriteContext writeCtxt, IOContext ctxt, OutputStream out) throws JacksonException {
        return new SfvGenerator(writeCtxt, ctxt,
                writeCtxt.getStreamWriteFeatures(_streamWriteFeatures),
                writeCtxt.getFormatWriteFeatures(_formatWriteFeatures),
                _createWriter(ctxt, out, JsonEncoding.UTF8),
                _defaultType);
    }

    @Override
    protected Writer _createWriter(IOContext ioCtxt, OutputStream out, JsonEncoding enc) throws JacksonException {
        return new tools.jackson.core.io.UTF8Writer(ioCtxt, out);
    }
}
