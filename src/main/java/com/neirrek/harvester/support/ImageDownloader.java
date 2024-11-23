/*
MIT License

Copyright (c) 2021-2024 Bruno Kerrien

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.neirrek.harvester.support;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.w3c.dom.Element;

public class ImageDownloader implements Callable<Boolean> {

    private final Mission mission;

    private String imageUrl;

    private final String saveRootDirectory;

    private final SaveMode saveMode;

    private final float compressionQuality;

    private final boolean force;

    private final AtomicInteger nbDownloadedImages;

    private final Logger logger;

    public ImageDownloader(Mission mission, String imageUrl, String saveRootDirectory, SaveMode saveMode,
        int jpgConversionRatio, boolean force, AtomicInteger nbDownloadedImages, Logger logger) {
        this.mission = mission;
        this.imageUrl = imageUrl;
        this.saveRootDirectory = saveRootDirectory;
        this.saveMode = saveMode;
        this.compressionQuality = (float) jpgConversionRatio / 100;
        this.force = force;
        this.nbDownloadedImages = nbDownloadedImages;
        this.logger = logger;
    }

    @Override
    public Boolean call() throws Exception {
        ImageFormat imageFormat = ImageFormat.forImageUrl(imageUrl);
        ImageFormat targetImageFormat = saveMode.targetImageFormat(imageFormat);
        String imagePath = mission.imagePath(imageUrl, saveRootDirectory, targetImageFormat);
        if (imagePath == null) {
            logger.info("/!\\ Unable to download image: {}", imageUrl);
            return false;
        }
        File file = new File(imagePath);
        boolean downloaded = false;
        boolean toDownload = !file.exists() || force;
        if (toDownload) {
            FileUtils.forceMkdirParent(file);
            int retry = 0;
            while (!downloaded && retry <= 5) {
                logImageDownload(toDownload, retry);
                InputStream bodyStream = null;
                try {
                    bodyStream = Jsoup.connect(imageUrl) //
                        .ignoreContentType(true) //
                        .timeout(0) //
                        .maxBodySize(0) //
                        .execute().bodyStream();
                    saveMode.saveImage(bodyStream, imageFormat, file, this);
                    nbDownloadedImages.getAndIncrement();
                    downloaded = true;
                } catch (IOException e) {
                    imageUrl = mission.alternateImageUrl(imageUrl);
                    retry++;
                } finally {
                    IOUtils.closeQuietly(bodyStream,
                        e -> logger.debug("Unable to close the response body stream: {}", e.getMessage()));
                }
            }
        } else {
            // logImageDownload(false, 0);
        }
        return downloaded;
    }

    private void logImageDownload(boolean toDownload, int retry) {
        if (logger.isInfoEnabled()) {
            String bullet = " ";
            if (toDownload) {
                bullet = retry > 0 ? "!" : "*";
            }
            logger.info(String.format("%s %s", bullet, imageUrl));
        }
    }

    void saveImageToFile(InputStream pngImageInputStream, File pngFile) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(pngFile)) {
            IOUtils.copy(pngImageInputStream, fileOutputStream);
        }
    }

    void convertPNGImageToJPG(InputStream pngImageInputStream, File jpgFile) throws IOException {
        ImageReader pngReader = ImageFormat.PNG.getImageReader();
        ImageWriter jpgWriter = ImageFormat.JPG.getImageWriter();
        try (ImageInputStream imageInputStream = new MemoryCacheImageInputStream(pngImageInputStream);
            ImageOutputStream outputStream = new FileImageOutputStream(jpgFile)) {
            ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
            jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpgWriteParam.setCompressionQuality(compressionQuality);
            pngReader.setInput(imageInputStream);
            BufferedImage pngImage = pngReader.read(0);
            IIOMetadata pngMetadata = pngReader.getImageMetadata(0);
            IIOMetadata jpgMetadata = null;
            ImageMetadata pngImageMetadata = ImageMetadata.from(pngMetadata);
            if (pngImageMetadata != null) {
                jpgMetadata = jpgWriter.getDefaultImageMetadata(new ImageTypeSpecifier(pngImage), jpgWriteParam);
                pngImageMetadata.fill(jpgMetadata);
            }
            IIOImage jpgImage = new IIOImage(pngImage, null, jpgMetadata);
            jpgWriter.setOutput(outputStream);
            jpgWriter.write(null, jpgImage, jpgWriteParam);
        } finally {
            jpgWriter.dispose();
            pngReader.dispose();
        }
    }

    private static class ImageMetadata {

        private String bitDepth;

        private String width;

        private String height;

        private ImageMetadata(String bitDepth, String width, String height) {
            this.bitDepth = bitDepth;
            this.width = width;
            this.height = height;
        }

        static ImageMetadata from(IIOMetadata metadata) {
            ImageFormat imageFormat = ImageFormat.forMetadataNativeFormatName(metadata.getNativeMetadataFormatName());
            if (imageFormat == null) {
                return null;
            }
            Element metadataTree = (Element) metadata.getAsTree(imageFormat.getMetadataNativeFormatName());
            Element metadataImageInfoTag = (Element) metadataTree
                .getElementsByTagName(imageFormat.getMetadataImageInfoTag()).item(0);
            String bitDepth = metadataImageInfoTag.getAttribute(imageFormat.getMetadataBitDepthAttribute());
            String width = metadataImageInfoTag.getAttribute(imageFormat.getMetadataWidthAttribute());
            String height = metadataImageInfoTag.getAttribute(imageFormat.getMetadataHeightAttribute());
            return new ImageMetadata(bitDepth, width, height);
        }

        void fill(IIOMetadata metadata) throws IIOInvalidTreeException {
            ImageFormat imageFormat = ImageFormat.forMetadataNativeFormatName(metadata.getNativeMetadataFormatName());
            Element metadataTree = (Element) metadata.getAsTree(imageFormat.getMetadataNativeFormatName());
            Element metadataImageInfoTag = (Element) metadataTree
                .getElementsByTagName(imageFormat.getMetadataImageInfoTag()).item(0);
            metadataImageInfoTag.setAttribute(imageFormat.getMetadataBitDepthAttribute(), bitDepth);
            metadataImageInfoTag.setAttribute(imageFormat.getMetadataWidthAttribute(), width);
            metadataImageInfoTag.setAttribute(imageFormat.getMetadataHeightAttribute(), height);
            metadata.setFromTree(imageFormat.getMetadataNativeFormatName(), metadataTree);
        }

    }

    enum ImageFormat {

        PNG {
            @Override
            String getMetadataNativeFormatName() {
                return "javax_imageio_png_1.0";
            }

            @Override
            String getMetadataImageInfoTag() {
                return "IHDR";
            }

            @Override
            String getMetadataBitDepthAttribute() {
                return "bitDepth";
            }

            @Override
            String getMetadataWidthAttribute() {
                return "width";
            }

            @Override
            String getMetadataHeightAttribute() {
                return "height";
            }
        }, //
        JPG {
            @Override
            String getMetadataNativeFormatName() {
                return "javax_imageio_jpeg_image_1.0";
            }

            @Override
            String getMetadataImageInfoTag() {
                return "sof";
            }

            @Override
            String getMetadataBitDepthAttribute() {
                return "samplePrecision";
            }

            @Override
            String getMetadataWidthAttribute() {
                return "samplesPerLine";
            }

            @Override
            String getMetadataHeightAttribute() {
                return "numLines";
            }
        };

        static ImageFormat forImageUrl(String imageUrl) {
            return ImageFormat.valueOf(StringUtils.upperCase(StringUtils.substringAfterLast(imageUrl, ".")));
        }

        static ImageFormat forMetadataNativeFormatName(String metadataNativeFormatName) {
            return Stream.of(ImageFormat.values()) //
                .filter(i -> StringUtils.equals(i.getMetadataNativeFormatName(), metadataNativeFormatName)) //
                .findAny() //
                .orElse(null);
        }

        String getExtension() {
            return new StringBuffer(".").append(getName()).toString();
        }

        String getName() {
            return name().toLowerCase();
        }

        ImageReader getImageReader() {
            return ImageIO.getImageReadersByFormatName(getName()).next();
        }

        ImageWriter getImageWriter() {
            return ImageIO.getImageWritersByFormatName(getName()).next();
        }

        abstract String getMetadataNativeFormatName();

        abstract String getMetadataImageInfoTag();

        abstract String getMetadataBitDepthAttribute();

        abstract String getMetadataWidthAttribute();

        abstract String getMetadataHeightAttribute();

    }

    public enum SaveMode {

        AS_IS {
            @Override
            ImageFormat targetImageFormat(ImageFormat originalImageFormat) {
                return originalImageFormat;
            }

            @Override
            void saveImage(InputStream imageInputStream, ImageFormat fromImageFormat, File imageFile,
                ImageDownloader imageDownloader) throws IOException {
                imageDownloader.saveImageToFile(imageInputStream, imageFile);
            }
        },
        CONVERT_TO_JPG {
            @Override
            ImageFormat targetImageFormat(ImageFormat originalImageFormat) {
                return ImageFormat.JPG;
            }

            @Override
            void saveImage(InputStream imageInputStream, ImageFormat fromImageFormat, File imageFile,
                ImageDownloader imageDownloader) throws IOException {
                if (fromImageFormat == ImageFormat.JPG) {
                    AS_IS.saveImage(imageInputStream, fromImageFormat, imageFile, imageDownloader);
                } else {
                    imageDownloader.convertPNGImageToJPG(imageInputStream, imageFile);
                }
            }
        };

        abstract ImageFormat targetImageFormat(ImageFormat originalImageFormat);

        abstract void saveImage(InputStream imageInputStream, ImageFormat fromImageFormat, File imageFile,
            ImageDownloader imageDownloader) throws IOException;

    }

}