// SFV (Structured Field Values) Main artifact Module descriptor
module tools.jackson.dataformat.sfv
{
    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.dataformat.sfv;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.sfv.SfvFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.sfv.SfvMapper;
}
