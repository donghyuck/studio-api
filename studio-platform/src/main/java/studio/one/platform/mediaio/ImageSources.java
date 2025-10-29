package studio.one.platform.mediaio;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

public final class ImageSources {

    private ImageSources() {
    }

    public static ImageSource of(Path path) {
        return new PathImageSource(path);
    }

    public static ImageSource of(byte[] bytes, String fileName, String contentType) {
        return new BytesImageSource(bytes, fileName, contentType);
    }

    public static ImageSource of(InputStream in, long size, String fileName, String contentType) {
        return new StreamImageSource(in, size, fileName, contentType);
    }

    public static ImageSource of(URL url, String fileName, String contentType, long sizeHint) {
        return new UrlImageSource(url, fileName, contentType, sizeHint);
    }

    public static ImageSource of(MultipartFile part) {
        return new MultipartFileImageSource(part);
    }    

    /* ---------- 구현체들 ---------- */

    static final class PathImageSource implements ImageSource {
        private final Path path;

        PathImageSource(Path path) {
            this.path = path;
        }

        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public long size() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public String fileName() {
            return path.getFileName() != null ? path.getFileName().toString() : null;
        }

        @Override
        public String contentType() {
            try {
                return Files.probeContentType(path);
            } catch (IOException e) {
                return null;
            }
        }
    }

    static final class BytesImageSource implements ImageSource {
        private final byte[] bytes;
        private final String fileName;
        private final String contentType;

        BytesImageSource(byte[] bytes, String fileName, String contentType) {
            this.bytes = bytes;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public String contentType() {
            return contentType;
        }
    }

    static final class StreamImageSource implements ImageSource {
        private final InputStream in;
        private final long size;
        private final String fileName;
        private final String contentType;
        private boolean closed = false;

        StreamImageSource(InputStream in, long size, String fileName, String contentType) {
            this.in = in;
            this.size = size;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        @Override
        public InputStream openStream() {
            return in;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                in.close();
                closed = true;
            }
        }
    }

    static final class UrlImageSource implements ImageSource {
        private final URL url;
        private final String fileName;
        private final String contentType;
        private final long sizeHint;
        private InputStream opened;

        UrlImageSource(URL url, String fileName, String contentType, long sizeHint) {
            this.url = url;
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeHint = sizeHint;
        }

        @Override
        public InputStream openStream() throws IOException {
            if (opened == null)
                opened = url.openStream();
            return opened;
        }

        @Override
        public long size() {
            return sizeHint;
        }

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public void close() throws IOException {
            if (opened != null)
                opened.close();
        }
    }
}
