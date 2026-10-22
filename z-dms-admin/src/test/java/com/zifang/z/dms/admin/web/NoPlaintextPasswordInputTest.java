package com.zifang.z.dms.admin.web;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形态 B 的**界面红线守卫**：控制台源码里**不许出现口令输入框**。
 *
 * <p>卡面口径：「界面全链路无明文口令输入框（只有 credentialRef 选择器）」——
 * 包括登记 / 编辑 / 自测各处。这条测试把那句口径变成会红的断言：
 * 谁在前端写下 {@code Input.Password} 或 {@code type="password"}，构建立刻失败。
 *
 * <p>扫描范围：{@code _frontend/z-dms-suit/src}（前端源码）。
 * 前端目录不存在时（只跑 core 的场景）跳过，不假装通过。
 */
class NoPlaintextPasswordInputTest {

    /** 前端源码目录（相对本模块）。 */
    private static final Path FRONTEND_SRC = Paths.get("..", "_frontend", "z-dms-suit", "src");

    /** 口令输入框的几种写法。 */
    private static final List<Pattern> FORBIDDEN = new ArrayList<>();

    static {
        FORBIDDEN.add(Pattern.compile("type\\s*=\\s*[\"']password[\"']", Pattern.CASE_INSENSITIVE));
        FORBIDDEN.add(Pattern.compile("Input\\.Password", Pattern.CASE_INSENSITIVE));
        FORBIDDEN.add(Pattern.compile("autoComplete\\s*=\\s*[\"'][^\"']*password", Pattern.CASE_INSENSITIVE));
        FORBIDDEN.add(Pattern.compile("name\\s*=\\s*[\"'](?:password|passwd|pwd|pass)[\"']",
                Pattern.CASE_INSENSITIVE));
        FORBIDDEN.add(Pattern.compile("\\b(?:password|passwd|pwd)\\b\\s*[:=]",
                Pattern.CASE_INSENSITIVE));
    }

    @Test
    void 前端源码里没有口令输入框() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(FRONTEND_SRC),
                "前端源码目录不存在（" + FRONTEND_SRC.toAbsolutePath() + "），跳过扫描");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(FRONTEND_SRC)) {
            for (Path file : files.filter(Files::isRegularFile).toArray(Path[]::new)) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (Pattern p : FORBIDDEN) {
                    if (p.matcher(content).find()) {
                        hits.add(file + " 命中 " + p.pattern());
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "前端出现口令输入框：" + hits);
    }

    @Test
    void 凭据面只有credentialRef选择器() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(FRONTEND_SRC), "前端源码目录不存在，跳过");
        Path form = FRONTEND_SRC.resolve(Paths.get("pages", "SourceFormDrawer.tsx"));
        Assumptions.assumeTrue(Files.isRegularFile(form), "找不到登记表单源码，跳过");
        String content = new String(Files.readAllBytes(form), StandardCharsets.UTF_8);
        assertTrue(content.contains("credentialRef"), "登记表单必须有 credentialRef 字段");
        assertTrue(content.contains("<Select"), "credentialRef 必须是选择器（Select），不是自由文本口令框");
    }
}
