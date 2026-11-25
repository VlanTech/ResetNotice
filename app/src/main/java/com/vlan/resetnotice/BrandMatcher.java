package com.vlan.resetnotice;

import java.util.Locale;

/** Match vendor or model names entered freely by the user. */
final class BrandMatcher {
    static String match(String name) {
        if (name == null) return "unknown";
        String v = name.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
        if (has(v, "githubcopilot", "copilot", "microsoft", "微软", "微軟", "副驾驶", "副駕駛")) return "copilot";
        if (has(v, "claude", "anthropic", "克劳德", "克勞德", "克洛德", "克神", "安索罗匹克", "安索羅匹克", "a厂", "a社") || v.equals("a/")) return "claude";
        if (has(v, "gemini", "googleai", "google", "gemma", "哈基米", "氢基米", "氫基米", "杰米尼", "傑米尼", "谷歌", "双子", "雙子") || v.equals("基米")) return "gemini";
        if (has(v, "deepseek", "深度求索", "深度寻索", "深度尋索", "深寻", "蓝鲸", "藍鯨", "小鲸鱼", "小鯨魚") || v.equals("ds")) return "deepseek";
        if (has(v, "chatgpt", "openai", "gpt", "codex", "鸡皮提", "雞皮提", "狗屁通", "狗屁梯", "欧喷ai", "歐噴ai", "做题龙", "做題龍", "astra", "o厂", "o社") || v.equals("o/") || v.equals("oai") || v.equals("sol") || v.equals("terra")) return "openai";
        if (has(v, "grok", "xai", "格罗克", "格羅克", "格洛克", "马斯克ai", "馬斯克ai", "马一龙", "馬一龍")) return "grok";
        if (has(v, "qwen", "tongyi", "alibaba", "通义", "通義", "千问", "千問", "阿里")) return "qwen";
        if (has(v, "perplexity", "pplx", "困惑ai")) return "perplexity";
        if (has(v, "kimi", "moonshot", "月之暗面")) return "kimi";
        if (has(v, "mistral", "lechat", "米斯特拉尔", "米斯特拉爾")) return "mistral";
        if (has(v, "doubao", "豆包", "豆子", "字节", "字節", "bytedance", "火山引擎")) return "doubao";
        if (has(v, "zhipu", "chatglm", "glm", "智谱", "智譜", "清言")) return "zhipu";
        if (has(v, "wenxin", "ernie", "baidu", "文心", "文小言", "百度")) return "wenxin";
        if (has(v, "minimax", "海螺")) return "minimax";
        if (has(v, "ollama")) return "ollama";
        if (has(v, "metaai", "llama", "meta", "羊驼", "羊駝", "拉玛", "拉瑪")) return "meta";
        if (has(v, "poe")) return "poe";
        if (has(v, "huggingface")) return "huggingface";
        return "unknown";
    }
    private static boolean has(String value, String... aliases) {
        for (String alias : aliases) if (value.contains(alias)) return true;
        return false;
    }
}
