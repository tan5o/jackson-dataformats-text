package tools.jackson.dataformat.sfv;

import tools.jackson.core.Version;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.cfg.MapperBuilderState;

/**
 * Convenience version of {@link ObjectMapper} which is configured
 * with {@link SfvFactory}.
 */
public class SfvMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * SFV backend.
     */
    public static class Builder extends MapperBuilder<SfvMapper, Builder>
    {
        public Builder(SfvFactory f) {
            super(f);
        }

        public Builder(StateImpl state) {
            super(state);
        }

        @Override
        public SfvMapper build() {
            return new SfvMapper(this);
        }

        @Override
        protected MapperBuilderState _saveState() {
            return new StateImpl(this);
        }

        /*
        /******************************************************************
        /* Format features
        /******************************************************************
         */

        public Builder enable(SfvReadFeature... features) {
            for (SfvReadFeature f : features) {
                _formatReadFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(SfvReadFeature... features) {
            for (SfvReadFeature f : features) {
                _formatReadFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(SfvReadFeature feature, boolean state) {
            if (state) {
                _formatReadFeatures |= feature.getMask();
            } else {
                _formatReadFeatures &= ~feature.getMask();
            }
            return this;
        }

        public Builder enable(SfvWriteFeature... features) {
            for (SfvWriteFeature f : features) {
                _formatWriteFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(SfvWriteFeature... features) {
            for (SfvWriteFeature f : features) {
                _formatWriteFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(SfvWriteFeature feature, boolean state) {
            if (state) {
                _formatWriteFeatures |= feature.getMask();
            } else {
                _formatWriteFeatures &= ~feature.getMask();
            }
            return this;
        }

        /**
         * Configure the default SFV type (ITEM, LIST, or DICTIONARY).
         */
        public Builder defaultType(SfvType type) {
            ((SfvFactory) _streamFactory).rebuild().defaultType(type).build();
            return this;
        }

        protected static class StateImpl extends MapperBuilderState
            implements java.io.Serializable
        {
            private static final long serialVersionUID = 1L;

            public StateImpl(Builder src) {
                super(src);
            }

            @Override
            protected Object readResolve() {
                return new Builder(this).build();
            }
        }
    }

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public SfvMapper() {
        this(new Builder(new SfvFactory()));
    }

    public SfvMapper(SfvFactory f) {
        this(new Builder(f));
    }

    public SfvMapper(Builder b) {
        super(b);
    }

    public static Builder builder() {
        return new Builder(new SfvFactory());
    }

    public static Builder builder(SfvFactory streamFactory) {
        return new Builder(streamFactory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder rebuild() {
        return new Builder((Builder.StateImpl) _savedBuilderState);
    }

    /*
    /**********************************************************************
    /* Life-cycle, shared "vanilla" (default configuration) instance
    /**********************************************************************
     */

    /**
     * Accessor method for getting globally shared "default" {@link SfvMapper}
     * instance: one that has default configuration, no modules registered, no
     * config overrides.
     */
    public static SfvMapper shared() {
        return SharedWrapper.wrapped();
    }

    /*
    /**********************************************************************
    /* Life-cycle: JDK serialization support
    /**********************************************************************
     */

    @Override
    protected Object writeReplace() {
        return _savedBuilderState;
    }

    @Override
    protected Object readResolve() {
        throw new IllegalStateException("Should never deserialize `" + getClass().getName() + "` directly");
    }

    /*
    /**********************************************************
    /* Basic accessor overrides
    /**********************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /**
     * Overridden with more specific type, since factory we have
     * is always of type {@link SfvFactory}
     */
    @Override
    public final SfvFactory tokenStreamFactory() {
        return (SfvFactory) _streamFactory;
    }

    /*
    /**********************************************************************
    /* Format-specific
    /**********************************************************************
     */

    public boolean isEnabled(SfvReadFeature f) {
        return _deserializationConfig.hasFormatFeature(f);
    }

    public boolean isEnabled(SfvWriteFeature f) {
        return _serializationConfig.hasFormatFeature(f);
    }

    /*
    /**********************************************************
    /* Helper class(es)
    /**********************************************************
     */

    /**
     * Helper class to contain dynamically constructed "shared" instance of
     * mapper, should one be needed via {@link #shared}.
     */
    private final static class SharedWrapper {
        private final static SfvMapper MAPPER = SfvMapper.builder().build();

        public static SfvMapper wrapped() { return MAPPER; }
    }
}
