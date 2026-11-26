uniform sampler2D u_Coverage;

const vec2 OFFS[8] = vec2[8](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 1.0,  1.0), vec2( 1.0, -1.0),
    vec2(-1.0,  1.0), vec2(-1.0, -1.0)
);

float blurFactor(bool outline, int width, int glowQuality, bool fastlines, bool fade)
{
    if (width == 0) return 0.0;

    float blurred = 0.0;

    ivec2 coord = ivec2(gl_FragCoord.xy);
    int last = textureSize(u_Coverage, 0).y - 1;

    for (int y = -width; y <= width; y++) {
        blurred += unpack16(texelFetch(u_Coverage, ivec2(coord.x, clamp(coord.y + y * glowQuality, 0, last)), 0).rg);
    }

    float norm = float((width * width + width) * 4);
    return clamp(blurred * float(2 * width + 1) / norm, 0.0, 1.0);
}
