package com.vlan.resetnotice;

import java.util.Locale;

/** Match vendor or model names entered freely by the user. */
final class BrandMatcher {
    static String match(String name) {
        if (name == null) return "unknown";
        String v = name.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
        // Products and coding harnesses take priority over their parent vendor/model.
        if (has(v, "antigravity", "反重力", "反重力")) return "antigravity";
        if (has(v, "xiaomimimo", "mimo", "小米")) return "xiaomimimo";
        if (has(v, "claudecode", "克劳德代码", "克勞德代碼") || v.equals("cc")) return "claudecode";
        if (has(v, "codex", "代码龙", "代碼龍")) return "codex";
        if (has(v, "cursor")) return "cursor";
        if (has(v, "windsurf", "风帆", "風帆", "codeium")) return "windsurf";
        if (has(v, "trae", "字节编程", "字節編程") || v.equals("崔")) return "trae";
        if (has(v, "cline")) return "cline";
        if (has(v, "roocode") || v.equals("roo")) return "roocode";
        if (has(v, "opencode")) return "opencode";
        if (has(v, "kiro")) return "kiro";
        if (has(v, "qoder")) return "qoder";
        if (has(v, "devin")) return "devin";
        if (has(v, "replit")) return "replit";
        if (has(v, "manus", "蝴蝶效应", "蝴蝶效應")) return "manus";
        if (has(v, "openrouter")) return "openrouter";
        if (has(v, "stepfun", "阶跃星辰", "階躍星辰", "跃问", "躍問")) return "stepfun";
        if (has(v, "hunyuan", "混元", "腾讯元宝", "騰訊元寶", "元宝", "元寶")) return "hunyuan";
        if (has(v, "siliconcloud", "siliconflow", "硅基流动", "硅基流動")) return "siliconcloud";
        if (has(v, "baichuan", "百川")) return "baichuan";
        if (has(v, "modelscope", "魔搭")) return "modelscope";
        if (has(v, "groq")) return "groq";
        if (has(v, "lovable")) return "lovable";
        if (v.equals("v0") || has(v, "vercelv0", "v0byvercel", "v0dev", "v0app", "v0pro", "v0premium")) return "v0";
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
        if (has(v, "zhipu", "chatglm", "glm", "智谱", "智譜", "清言") || v.equals("zai") || v.startsWith("zaipro")) return "zhipu";
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
