#version 450

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outColor;

layout(push_constant) uniform Push {
    vec2 resolution;
    float time;
    uint frameIndex;
} pc;

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float sdSphere(vec3 p, float r) { return length(p) - r; }

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

vec2 scene(vec3 p) {
    vec3 q = p;
    q.xz *= rot(pc.time * 0.31);
    q.xy *= rot(pc.time * 0.19);
    float torus = sdTorus(q, vec2(1.15, 0.22));

    vec3 crystalP = p;
    crystalP.y += sin(pc.time * 0.7) * 0.15;
    crystalP.xz *= rot(-pc.time * 0.24);
    float crystal = sdBox(crystalP, vec3(0.48, 0.82, 0.48));
    crystal = max(crystal, dot(abs(crystalP), normalize(vec3(1.0))) - 0.88);

    vec3 orbit = p - vec3(cos(pc.time) * 1.75, sin(pc.time * 1.3) * 0.55, sin(pc.time) * 1.75);
    float satellite = sdSphere(orbit, 0.24);

    vec2 hit = vec2(torus, 1.0);
    if (crystal < hit.x) hit = vec2(crystal, 2.0);
    if (satellite < hit.x) hit = vec2(satellite, 3.0);
    return hit;
}

vec3 normalAt(vec3 p) {
    const vec2 e = vec2(0.0015, 0.0);
    float d = scene(p).x;
    return normalize(vec3(
        scene(p + e.xyy).x - d,
        scene(p + e.yxy).x - d,
        scene(p + e.yyx).x - d
    ));
}

float softShadow(vec3 ro, vec3 rd) {
    float shade = 1.0;
    float t = 0.04;
    for (int i = 0; i < 14; ++i) {
        float h = scene(ro + rd * t).x;
        shade = min(shade, 10.0 * h / t);
        t += clamp(h, 0.025, 0.22);
    }
    return clamp(shade, 0.12, 1.0);
}

void main() {
    vec2 p = (gl_FragCoord.xy * 2.0 - pc.resolution) / pc.resolution.y;
    float t = pc.time;
    vec3 ro = vec3(3.7 * cos(t * 0.17), 1.55 + 0.28 * sin(t * 0.23), 3.7 * sin(t * 0.17));
    vec3 target = vec3(0.0, 0.05, 0.0);
    vec3 forward = normalize(target - ro);
    vec3 right = normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, forward);
    vec3 rd = normalize(forward * 1.75 + right * p.x + up * p.y);

    float travel = 0.0;
    float material = 0.0;
    float glowA = 0.0;
    float glowB = 0.0;
    vec3 pos = ro;
    bool hit = false;
    for (int i = 0; i < 56; ++i) {
        pos = ro + rd * travel;
        vec2 d = scene(pos);
        glowA += 0.010 / (0.035 + abs(d.x));
        glowB += 0.006 / (0.025 + abs(sdTorus(pos.zyx, vec2(2.25, 0.035))));
        if (d.x < 0.0025) {
            material = d.y;
            hit = true;
            break;
        }
        travel += clamp(d.x * 0.72, 0.008, 0.28);
        if (travel > 9.0) break;
    }

    vec3 color = vec3(0.006, 0.009, 0.022);
    float gridX = exp(-18.0 * abs(fract(p.x * 3.0 + t * 0.03) - 0.5));
    float gridY = exp(-18.0 * abs(fract(p.y * 3.0) - 0.5));
    color += vec3(0.01, 0.045, 0.08) * (gridX + gridY) * 0.3;

    if (hit) {
        vec3 n = normalAt(pos);
        vec3 lightPos = vec3(2.8 * cos(t * 0.6), 2.6, 2.8 * sin(t * 0.6));
        vec3 l = normalize(lightPos - pos);
        vec3 h = normalize(l - rd);
        float diff = max(dot(n, l), 0.0);
        float spec = pow(max(dot(n, h), 0.0), 48.0);
        float shadow = softShadow(pos + n * 0.01, l);
        vec3 base = material < 1.5 ? vec3(0.03, 0.55, 1.0)
                  : material < 2.5 ? vec3(0.75, 0.08, 1.0)
                                   : vec3(1.0, 0.32, 0.04);
        float fresnel = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
        color += base * (0.13 + diff * shadow * 0.9) + spec * vec3(1.0, 0.75, 1.0);
        color += base * fresnel * 1.2;
    }

    color += vec3(0.02, 0.32, 0.85) * min(glowA * glowA * 0.018, 1.8);
    color += vec3(0.75, 0.04, 1.0) * min(glowB * glowB * 0.015, 1.2);
    color *= exp(-0.035 * travel * travel);
    color = 1.0 - exp(-color * 1.35);
    color = pow(color, vec3(0.4545));
    float dither = float((pc.frameIndex * 1664525u + uint(gl_FragCoord.x) * 1013904223u) & 255u) / 255.0;
    color += (dither - 0.5) / 255.0;
    outColor = vec4(color, 1.0);
}
