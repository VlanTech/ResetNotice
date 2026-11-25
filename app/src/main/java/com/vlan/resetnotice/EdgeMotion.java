package com.vlan.resetnotice;

/** The translated edge uses measured kinematics, followed by a critically
 * damped spring with matching initial position and velocity. */
final class EdgeMotion {
    static final float OMEGA = 9f;
    static final class Tracker {
        private long time;
        private float velocity, acceleration;
        void seed(float v, long millis) { velocity = v; acceleration = 0; time = millis; }
        void sample(float displacement, long millis) {
            long span = millis - time;
            if (span <= 0) return;
            if (span > 160) { seed(0f, millis); return; }
            float dt = span / 1000f;
            float measured = displacement / dt;
            float next = 0.30f * velocity + 0.70f * measured;
            acceleration = 0.55f * acceleration + 0.45f * (next - velocity) / dt;
            velocity = next; time = millis;
        }
        float predict(long millis, float lookAhead) {
            if (millis - time > 100) return 0f;
            float horizon = Math.max(0f, Math.min(0.024f, lookAhead));
            float predicted = velocity + acceleration * horizon;
            // A noisy derivative cannot invent a direction reversal at impact.
            return velocity != 0f && velocity * predicted < 0f ? 0f : predicted;
        }
        float acceleration() { return acceleration; }
    }
    static float position(float x0, float v0, double t) {
        return (float)((x0 + (v0 + OMEGA * x0) * t) * Math.exp(-OMEGA * t));
    }
    static float velocity(float x0, float v0, double t) {
        return (float)((v0 - OMEGA * (v0 + OMEGA * x0) * t) * Math.exp(-OMEGA * t));
    }
    static float rubber(float raw, float density) {
        return (float)(Math.signum(raw) * 170f * density * Math.log1p(Math.abs(raw) / (350f * density)));
    }
    static float inverseRubber(float translated, float density) {
        return (float)(Math.signum(translated) * 350f * density * Math.expm1(Math.abs(translated) / (170f * density)));
    }
}
