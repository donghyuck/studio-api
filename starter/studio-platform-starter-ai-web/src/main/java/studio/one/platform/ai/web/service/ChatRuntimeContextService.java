package studio.one.platform.ai.web.service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatRuntimeContext;

/** Conservative system-time routing. Mixed/business/document questions never use this shortcut. */
public final class ChatRuntimeContextService {
    private static final Pattern DATE = Pattern.compile(
            "(?:오늘(?:은|이)?(?:날짜(?:는|가)?)?(?:몇일|며칠|몇월며칠|무슨요일)(?:이야|인가요|이니|이지|일까|입니까)?"
            + "|(?:오늘|현재)(?:의)?날짜(?:를)?(?:알려줘|알려주세요|알려줄래|말해줘)?"
            + "|오늘날짜(?:는|가)(?:뭐야|무엇인가요)"
            + "|what(?:is|s)(?:the)?(?:date|day)(?:today)?|what(?:date|day)isit(?:today)?|what(?:is|s)todaysdate)");
    private static final Pattern TIME = Pattern.compile(
            "(?:(?:지금|현재)(?:은|의)?(?:시간(?:은|이)?)?몇시(?:야|인가요|니|지|입니까)?"
            + "|(?:지금|현재)시간(?:을)?(?:알려줘|알려주세요)?|whattimeisit(?:now)?)");
    private static final Pattern TEMPORAL = Pattern.compile(
            "오늘|내일|어제|현재|지금|이번\\s*(?:주|달|월|년)|최근|최신|\\b(?:today|tomorrow|yesterday|current|now|latest|this\\s+(?:week|month|year))\\b",
            Pattern.CASE_INSENSITIVE);
    private final Clock clock;
    private final ZoneId zone;

    public ChatRuntimeContextService(Clock clock, ZoneId zone) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public Optional<ChatResponse> answer(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT).strip()
                .replaceAll("[\\s?？.!'’]", "");
        boolean date = DATE.matcher(normalized).matches();
        if (!date && !TIME.matcher(normalized).matches()) return Optional.empty();
        ChatRuntimeContext context = ChatRuntimeContext.capture(clock, zone);
        String text = date
                ? "오늘은 " + context.localTime().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)) + "입니다."
                : "현재 시각은 " + context.localTime().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm:ss", Locale.KOREAN)) + "입니다.";
        text += " (" + zone.getId() + " 기준)";
        return Optional.of(new ChatResponse(List.of(ChatMessage.assistant(text)), "",
                Map.of("answerSource", "SYSTEM_CONTEXT", "effectiveTimezone", zone.getId(),
                        "asOf", context.asOf().toString(), "routeReason", "EXACT_SYSTEM_TIME_QUERY",
                        "canonicalContent", text, "ragAnswerCache", "BYPASS")));
    }

    public boolean isTimeDependent(String question) {
        return question != null && TEMPORAL.matcher(question).find();
    }
}
