package studio.one.platform.mediaio;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

public final class MultipartFileImageSource implements ImageSource {
    
    private final MultipartFile part;

    public MultipartFileImageSource(MultipartFile part) {
        this.part = part;
    }

    @Override
    public InputStream openStream() throws IOException {
        return part.getInputStream();
    }

    @Override
    public long size() {
        return part.getSize();
    }

    @Override
    public String fileName() {
        return part.getOriginalFilename();
    }

    @Override
    public String contentType() {
        return part.getContentType();
    }

    @Override
    public void close() {
        /* Spring이 관리 */ }
}
