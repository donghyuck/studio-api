package studio.one.platform.mediaio;

import java.io.IOException;
import java.io.InputStream;

/**
 * 바이트 소스를 통일한 인터페이스.
 * - 반드시 try-with-resources로 close() 호출 (Stream 기반은 내부 스트림도 닫힘)
 * - size()는 -1이면 미상(unknown)
 * - fileName()/contentType()은 null 허용
 */
public interface ImageSource extends AutoCloseable {

    /** 읽기용 스트림(호출자가 닫을 필요 없음; close()가 전체 생명주기 관리) */
    InputStream openStream() throws IOException;

    /** 바이트 길이(미상이면 -1) */
    long size();

    /** 원본/상대 파일명(없으면 null) */
    String fileName();

    /** MIME 타입(없으면 null) */
    String contentType();

    /** 기본 no-op. 구현체에 따라 내부 스트림/자원을 해제 */
    @Override
    default void close() throws IOException {
        /* no-op */ }
}