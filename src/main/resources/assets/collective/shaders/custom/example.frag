vec4 mainImage(bool outline) {
    vec4 color = outline ? u_OutlineColor : u_FillColor;
    vec2 uv = (2.0 * gl_FragCoord.xy - u_Size) / min(u_Size.x, u_Size.y);
    uv.x += 0.6 * cos(2.5 * uv.x + u_Time);
    uv.y += 0.6 * cos(2.5 * uv.y + u_Time);

    return vec4(
        color.r / abs(sin(u_Time -uv.y - uv.x)),
        color.g / abs(sin(u_Time -uv.y - uv.x)),
        color.b / abs(sin(u_Time -uv.y - uv.x)),
        color.a
    );
}