package tools.jackson.dataformat.sfv;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.util.JsonGeneratorDelegate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

final class SfvGenerator extends JsonGeneratorDelegate {
    private final IOContext _ioContext;
    private final TokenBuffer _buffer;
    private final OutputStream _out;
    private final Writer _writer;
    private final SfvType _defaultType;
    private final int _streamWriteFeatures;
    private boolean _closed;

    SfvGenerator(ObjectWriteContext writeCtxt, IOContext ioContext, int streamWriteFeatures, int sfvFeatures,
            Writer out, SfvType defaultType) {
        super(new TokenBuffer(writeCtxt, false), false);
        _buffer = (TokenBuffer) delegate;
        _ioContext = ioContext;
        _out = null;
        _writer = out;
        _defaultType = defaultType;
        _streamWriteFeatures = streamWriteFeatures;
    }

    @Override
    public void close() {
        if (_closed) {
            return;
        }
        _closed = true;
        try {
            super.close();
            JsonNode node = readTreeFromBuffer();
            String sfv = SfvCodec.write(node, _defaultType);
            writeOutput(sfv);
        } catch (IOException e) {
            throw new StreamWriteException(this, e.getMessage(), e);
        } finally {
            _ioContext.close();
        }
    }

    @Override
    public void flush() {
        try {
            if (_writer != null) {
                if (_isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    _writer.flush();
                }
            } else if (_out != null) {
                if (_isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    _out.flush();
                }
            }
        } catch (IOException e) {
            throw new StreamWriteException(this, e.getMessage(), e);
        }
    }

    private JsonNode readTreeFromBuffer() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = _buffer.asParser(ObjectReadContext.empty());
        return mapper.readTree(parser);
    }

    private void writeOutput(String sfv) throws IOException {
        if (_writer != null) {
            _writer.write(sfv);
            closeIfNeeded(_writer);
            return;
        }
        if (_out != null) {
            _out.write(sfv.getBytes(StandardCharsets.UTF_8));
            closeIfNeeded(_out);
        }
    }

    private void closeIfNeeded(OutputStream out) throws IOException {
        if (_ioContext.isResourceManaged() || _isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
            out.close();
        } else if (_isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            out.flush();
        }
    }

    private void closeIfNeeded(Writer out) throws IOException {
        if (_ioContext.isResourceManaged() || _isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
            out.close();
        } else if (_isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            out.flush();
        }
    }

    private boolean _isEnabled(StreamWriteFeature feature) {
        return (feature.getMask() & _streamWriteFeatures) != 0;
    }
}
