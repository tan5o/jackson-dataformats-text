package tools.jackson.dataformat.sfv;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines togglable features for SFV generators.
 */
public enum SfvWriteFeature implements FormatFeature {
    /**
     * Placeholder feature for future SFV generation options.
     */
    RESERVED(false);

    private final boolean _defaultState;
    private final int _mask;

    public static int collectDefaults() {
        int flags = 0;
        for (SfvWriteFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    SfvWriteFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override
    public boolean enabledByDefault() { return _defaultState; }

    @Override
    public boolean enabledIn(int flags) { return (flags & _mask) != 0; }

    @Override
    public int getMask() { return _mask; }
}
