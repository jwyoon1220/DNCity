// AGENT-DONE(claude): sodium-gpu-hiz-occlusion
#version 150 core

// Farthest-depth reduction of the terrain depth buffer to the small Hi-Z image (see GpuDepthCapture / HiZBuffer).
// Each output texel covers the source block [floor(d * src / dst), ceil((d + 1) * src / dst)) of the viewport, so
// every source texel is read by at least one output texel (neighbouring blocks may overlap by one, which only makes
// the result farther, i.e. more conservative). Standard depth range: larger = farther, so the reduction is max().

uniform sampler2D u_Depth;
uniform ivec4 u_SrcRect; // viewport x, y, width, height in the depth texture
uniform ivec2 u_DstSize;

out vec4 out_Depth;

void main() {
    ivec2 d = ivec2(gl_FragCoord.xy);
    ivec2 lo = (d * u_SrcRect.zw) / u_DstSize;
    ivec2 hi = ((d + 1) * u_SrcRect.zw + u_DstSize - 1) / u_DstSize;
    hi = clamp(max(hi, lo + 1), ivec2(1), u_SrcRect.zw);

    float farthest = 0.0;

    for (int y = lo.y; y < hi.y; y++) {
        for (int x = lo.x; x < hi.x; x++) {
            farthest = max(farthest, texelFetch(u_Depth, u_SrcRect.xy + ivec2(x, y), 0).r);
        }
    }

    out_Depth = vec4(farthest, 0.0, 0.0, 1.0);
}
