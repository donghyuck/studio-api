package studio.one.platform.thumbnail.renderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;

import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailRenderLimits;

public class BatikEpubSvgRasterizer implements EpubSvgRasterizer {

    @Override
    public BufferedImage rasterize(
            byte[] svgBytes,
            int targetSize,
            long maxSourcePixels,
            String sourceDescription) {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) targetSize);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, (float) targetSize);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.FALSE);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOWED_SCRIPT_TYPES, "");
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_CONSTRAIN_SCRIPT_ORIGIN, Boolean.TRUE);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, Boolean.FALSE);

        try (ByteArrayInputStream input = new ByteArrayInputStream(svgBytes);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(output));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));
            if (image == null) {
                throw new ThumbnailGenerationException("Failed to decode rasterized EPUB SVG cover");
            }
            ThumbnailRenderLimits.requirePixelsWithinLimit(
                    image.getWidth(), image.getHeight(), maxSourcePixels, sourceDescription);
            return image;
        } catch (TranscoderException | IOException ex) {
            throw new ThumbnailGenerationException("Failed to rasterize EPUB SVG cover", ex);
        }
    }
}
