package com.vlan.resetnotice;

import java.util.Locale;

/** Match vendor or model names entered freely by the user. */
final class BrandMatcher {
    static String match(String name) {
        if (name == null) return "unknown";
        String v = name.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
        if (has(v, "githubcopilot", "copilot", "microsoft")) return "copilot";
        if (has(v, "claude", "anthropic", "克劳德", "克勞德")) return "claude";
        if (has(v, "gemini", "googleai", "google", "gemma", "哈基米", "双子", "雙子")) return "gemini";
        if (has(v, "deepseek", "深度求索")) return "deepseek";
        if (has(v, "chatgpt", "openai", "gpt", "codex")) return "openai";
        if (has(v, "grok", "xai")) return "grok";
        if (has(v, "qwen", "tongyi", "alibaba", "通义", "通義", "千问", "千問", "阿里")) return "qwen";
        if (has(v, "perplexity")) return "perplexity";
        if (has(v, "kimi", "moonshot", "月之暗面")) return "kimi";
        if (has(v, "mistral", "lechat")) return "mistral";
        if (has(v, "doubao", "豆包", "字节", "字節", "bytedance")) return "doubao";
        if (has(v, "zhipu", "chatglm", "glm", "智谱", "智譜")) return "zhipu";
        if (has(v, "wenxin", "ernie", "baidu", "文心", "百度")) return "wenxin";
        if (has(v, "minimax", "海螺")) return "minimax";
        if (has(v, "ollama")) return "ollama";
        if (has(v, "metaai", "llama", "meta")) return "meta";
        if (has(v, "poe")) return "poe";
        if (has(v, "huggingface")) return "huggingface";
        return "unknown";
    }
    private static boolean has(String value, String... aliases) {
        for (String alias : aliases) if (value.contains(alias)) return true;
        return false;
    }
}
