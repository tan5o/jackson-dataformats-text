package tools.jackson.dataformat.sfv;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.base.DecorableTSFactory;

/**
 * {@link tools.jackson.core.TSFBuilder} implementation for constructing
 * {@link SfvFactory} instances.
 */
public class SfvFactoryBuilder extends DecorableTSFactory.DecorableTSFBuilder<SfvFactory, SfvFactoryBuilder> {
    private SfvType _defaultType = SfvType.LIST;

    SfvFactoryBuilder() {
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                SfvFactory.DEFAULT_SFV_PARSER_FEATURE_FLAGS,
                SfvFactory.DEFAULT_SFV_GENERATOR_FEATURE_FLAGS);
    }

    SfvFactoryBuilder(SfvFactory base) {
        super(base);
        _defaultType = base.defaultType();
    }

    @Override
    public SfvFactory build() {
        return new SfvFactory(this);
    }

    public SfvFactoryBuilder defaultType(SfvType type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        _defaultType = type;
        return this;
    }

    public SfvType defaultType() {
        return _defaultType;
    }

    public SfvFactoryBuilder enable(SfvReadFeature f) {
        _formatReadFeatures |= f.getMask();
        return this;
    }

    public SfvFactoryBuilder enable(SfvReadFeature first, SfvReadFeature... other) {
        _formatReadFeatures |= first.getMask();
        for (SfvReadFeature f : other) {
            _formatReadFeatures |= f.getMask();
        }
        return this;
    }

    public SfvFactoryBuilder disable(SfvReadFeature f) {
        _formatReadFeatures &= ~f.getMask();
        return this;
    }

    public SfvFactoryBuilder disable(SfvReadFeature first, SfvReadFeature... other) {
        _formatReadFeatures &= ~first.getMask();
        for (SfvReadFeature f : other) {
            _formatReadFeatures &= ~f.getMask();
        }
        return this;
    }

    public SfvFactoryBuilder configure(SfvReadFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    public SfvFactoryBuilder enable(SfvWriteFeature f) {
        _formatWriteFeatures |= f.getMask();
        return this;
    }

    public SfvFactoryBuilder enable(SfvWriteFeature first, SfvWriteFeature... other) {
        _formatWriteFeatures |= first.getMask();
        for (SfvWriteFeature f : other) {
            _formatWriteFeatures |= f.getMask();
        }
        return this;
    }

    public SfvFactoryBuilder disable(SfvWriteFeature f) {
        _formatWriteFeatures &= ~f.getMask();
        return this;
    }

    public SfvFactoryBuilder disable(SfvWriteFeature first, SfvWriteFeature... other) {
        _formatWriteFeatures &= ~first.getMask();
        for (SfvWriteFeature f : other) {
            _formatWriteFeatures &= ~f.getMask();
        }
        return this;
    }

    public SfvFactoryBuilder configure(SfvWriteFeature f, boolean state) {
        return state ? enable(f) : disable(f);
    }
}
