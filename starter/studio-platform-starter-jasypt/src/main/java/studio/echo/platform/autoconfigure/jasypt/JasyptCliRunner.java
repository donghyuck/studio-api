/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file JasyptCliRunner.java
 *      @date 2025
 *
 */



package studio.echo.platform.autoconfigure.jasypt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
/**
 *
 * @author  donghyuck, son
 * @since 2025-08-11
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-11  donghyuck, son: 최초 생성.
 * </pre>
 */

 @Slf4j
final class JasyptCliRunner implements ApplicationRunner {

    // ====== 옵션 키(중복 문자열 상수화) ======
    private static final String K_CLI       = "jasypt.cli";
    private static final String K_ENCRYPT   = "jasypt.encrypt";
    private static final String K_DECRYPT   = "jasypt.decrypt";
    private static final String K_IN        = "jasypt.in";
    private static final String K_OUT       = "jasypt.out";
    private static final String K_WRAP      = "jasypt.wrap";            // encrypt 시 ENC(...) 감쌀지
    private static final String K_HELP      = "help";
    private static final String K_HELP_S    = "h";

    private static final boolean DEFAULT_WRAP = true;

    private static final Pattern ENC_WRAPPER = Pattern.compile("^ENC\\((.*)\\)$", Pattern.DOTALL);

    private final StringEncryptor encryptor;

    JasyptCliRunner(final StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public void run(final ApplicationArguments args) {
        // 사용 시그널이 없으면 아무것도 안 하고 정상 부팅
        if (!(args.containsOption(K_CLI) || args.containsOption(K_ENCRYPT) || args.containsOption(K_DECRYPT))) {
            return;
        }
        if (args.containsOption(K_HELP) || args.containsOption(K_HELP_S)) {
            printUsage();
            return;
        }

        final boolean doEncrypt = args.containsOption(K_ENCRYPT);
        final boolean doDecrypt = args.containsOption(K_DECRYPT);

        if (doEncrypt == doDecrypt) { // 둘 다 or 둘 다 아님 → 잘못된 사용
            warnAndUsage("Specify exactly one of --%s or --%s", K_ENCRYPT, K_DECRYPT);
            return;
        }

        final String in  = first(args.getOptionValues(K_IN));   // 파일 경로 또는 "-"(stdin)
        final String out = first(args.getOptionValues(K_OUT));  // 파일 경로 또는 "-"(stdout)
        final boolean wrap = parseBoolean(first(args.getOptionValues(K_WRAP)), DEFAULT_WRAP);

        try {
            if (doEncrypt) {
                execute(args.getOptionValues(K_ENCRYPT), in, out, s -> wrap ? "ENC(" + encryptor.encrypt(s) + ")" : encryptor.encrypt(s));
            } else {
                execute(args.getOptionValues(K_DECRYPT), in, out, this::decryptSmart);
            }
        } catch (IOException io) {
            log.error("I/O error while processing Jasypt CLI", io);
        } catch (IllegalArgumentException iae) {
            warnAndUsage(iae.getMessage());
        } catch (RuntimeException re) {
            // 예기치 못한 오류는 로깅 후 부팅 유지
            log.error("Unexpected error in Jasypt CLI", re);
        }
    }

    private void execute(final List<String> values,
                         final String in,
                         final String out,
                         final UnaryOperator<String> op) throws IOException {

        if (StringUtils.hasText(in)) {
            // 입력: 파일 또는 STDIN
            if ("-".equals(in)) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, UTF_8));
                     PrintWriter pw = openWriter(out)) {
                    processStream(br, pw, op);
                }
            } else {
                final Path inPath = Path.of(in);
                if (!Files.exists(inPath) || !Files.isRegularFile(inPath)) {
                    throw new IllegalArgumentException("Input file not found: " + in);
                }
                try (BufferedReader br = Files.newBufferedReader(inPath, UTF_8);
                     PrintWriter pw = openWriter(out)) {
                    processStream(br, pw, op);
                }
            }
            return;
        }

        // 값 인자로 처리
        if (values != null && !values.isEmpty()) {
            try (PrintWriter pw = openWriter(out)) {
                for (String v : values) {
                    if (StringUtils.hasText(v)) {
                        pw.println(op.apply(v));
                    }
                }
                pw.flush();
            }
            return;
        }

        throw new IllegalArgumentException("No input provided. Use --" + K_IN + "=FILE|- or --" + K_ENCRYPT + "=val / --" + K_DECRYPT + "=val");
    }


    private void processStream(final BufferedReader br,
                            final PrintWriter pw,
                            final UnaryOperator<String> op) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.isEmpty()) {
                pw.println(op.apply(line));
            }
        }
        pw.flush();
    }

    private String decryptSmart(final String s) {
        final var m = ENC_WRAPPER.matcher(s);
        final String text = m.matches() ? m.group(1) : s;
        return encryptor.decrypt(text);
    }

    private static String first(final List<String> values) {
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static boolean parseBoolean(final String v, final boolean def) {
        if (!StringUtils.hasText(v)) return def;
        return Boolean.parseBoolean(v);
    }

    // S106 억제: CLI 파이프 용도로만 stdout 사용, 한 곳으로 모아 justification 남김
    @SuppressWarnings("java:S106")
    private static void printUsage() {
        System.out.println(
            "\nJasypt CLI\n" +
            "Encrypt:\n" +
            "  --" + K_ENCRYPT + "=secret [--" + K_WRAP + "=true]\n" +
            "  --" + K_ENCRYPT + " --" + K_IN + "=-\n" +
            "  --" + K_ENCRYPT + " --" + K_IN + "=/path/in.txt --" + K_OUT + "=/path/out.txt\n" +
            "Decrypt:\n" +
            "  --" + K_DECRYPT + "='ENC(...)'  |  --" + K_DECRYPT + " --" + K_IN + "=-\n" +
            "Options:\n" +
            "  --" + K_IN + "=FILE|-       input file or '-'(stdin)\n" +
            "  --" + K_OUT + "=FILE|-      output file (default stdout)\n" +
            "  --" + K_WRAP + "=true|false  wrap ENC(...) on encrypt (default true)\n"
        );
    }

    private void warnAndUsage(final String fmt, final Object... args) {
        final String msg = String.format(fmt, args);
        log.warn("[Jasypt-CLI] {}", msg);
        printUsage();
    }

    // stdout/파일 열기는 한 곳에서만
    @SuppressWarnings("java:S106")
    private static PrintWriter openWriter(final String out) throws IOException {
        if (!StringUtils.hasText(out) || "-".equals(out)) {
            return new PrintWriter(new OutputStreamWriter(System.out, UTF_8), true);
        }
        final Path outPath = Path.of(out);
        final Path parent = outPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        return new PrintWriter(Files.newBufferedWriter(outPath, UTF_8), true);
    }
}