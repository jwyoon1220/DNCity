#version 150

uniform sampler2D DiffuseSampler;
uniform float Amount;

in vec2 texCoord;
out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Amount > 0: morphine saturation boost
// Amount < 0: suppression desaturation (stronger pull toward gray)
void main() {
    vec2 uv = mix(vec2(0.003), vec2(0.997), clamp(texCoord, 0.0, 1.0));
    vec4 color = texture(DiffuseSampler, uv);
    vec3 rgb = max(color.rgb, vec3(0.0));
    vec3 hsv = rgb2hsv(rgb);
    float amount = clamp(Amount, -1.25, 1.2);

    if (amount >= 0.0) {
        hsv.y = clamp(hsv.y * (1.0 + amount), 0.0, 1.0);
        fragColor = vec4(hsv2rgb(hsv), 1.0);
    } else {
        float desat = -amount;
        // Hard gray pull so high suppression is unmistakable.
        hsv.y = clamp(hsv.y * (1.0 - desat * 0.98), 0.0, 1.0);
        // Slight cool lift so desat reads as "shock gray" not just dull.
        vec3 grayed = hsv2rgb(hsv);
        vec3 cool = mix(grayed, vec3(0.78, 0.80, 0.84), desat * 0.18);
        fragColor = vec4(cool, 1.0);
    }
}
