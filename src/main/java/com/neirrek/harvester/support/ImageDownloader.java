/*
 * MIT License
 * 
 * Copyright (c) 2021-2025 Bruno Kerrien
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

/**
 * The ImageDownloader class is responsible for downloading images from provided URLs and saving them
 * to a specified directory. It supports various save modes, image formats, and optional compression
 * quality for image conversion.
 *
 * This class implements the Callable interface to be used in a multithreaded environment.
 */
public class ImageDownloader implements Callable<Boolean> {

    private final Mission mission;

    private String imageUrl;

    private final String saveRootDirectory;

    private final SaveMode saveMode;

    private final float compressionQuality;

    private final boolean force;

    private final AtomicInteger nbDownloadedImages;

    private final Logger logger;

    /**
     * Constructs an ImageDownloader instance to download and process images based on the specified parameters.
     *
     * @param mission The mission associated with the image (e.g., CURIOSITY, PERSEVERANCE).
     * @param imageUrl The URL of the image to be downloaded.
     * @param saveRootDirectory The root directory where the downloaded image will be saved.
     * @param saveMode The save mode specifying how the image should be processed (e.g., AS_IS or CONVERT_TO_JPG).
     * @param jpgConversionRatio The compression quality ratio for JPG conversion (percentage from 0 to 100).
     * @param force A boolean indicating whether to overwrite existing files.
     * @param nbDownloadedImages An AtomicInteger to track the number of successfully downloaded images.
     * @param logger The Logger instance used for logging download activities.
     */
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

    /**
     * Downloads an image from the specified URL, performs necessary processing based on the settings,
     * and saves the image to the designated location. This method checks whether the image already
     * exists and whether it should be overwritten based on the `force` parameter. If the image needs
     * to be downloaded, it retries the download up to a maximum of 5 attempts in case of failure.
     *
     * The image format is determined based on the URL, and the appropriate target format and file
     * path are calculated. The image is then saved using the provided save mode and additional
     * configurations.
     *
     * @return A Boolean indicating whether the image was successfully downloaded and saved.
     *         Returns {@code true} if the operation was successful, and {@code false} otherwise.
     * @throws Exception If an unexpected error occurs during the image download or save process.
     */
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
                logImageDownload(true, retry);
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
            logger.info("{} {}", bullet, imageUrl);
        }
    }

    /**
     * Saves a PNG image from the provided input stream to the specified file.
     *
     * @param pngImageInputStream The input stream containing the PNG image data to be saved.
     * @param pngFile The file where the PNG image will be saved.
     * @throws IOException If an I/O error occurs during the save operation.
     */
    void saveImageToFile(InputStream pngImageInputStream, File pngFile) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(pngFile)) {
            IOUtils.copy(pngImageInputStream, fileOutputStream);
        }
    }

    /**
     * Converts a PNG image from the input stream into a JPG image and writes it to the specified file.
     * This method handles reading and writing image metadata, applying compression settings, and ensuring
     * the conversion process is accurate between the two image formats.
     *
     * @param pngImageInputStream The input stream containing the PNG image data to convert.
     * @param jpgFile The target file where the converted JPG image will be saved.
     * @throws IOException If an I/O error occurs during the conversion or write operation.
     */
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

    private record ImageMetadata(String bitDepth, String width, String height) {

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

    /**
     * Represents an enumeration of supported image formats and provides utility methods
     * for handling image metadata, extensions, and format-specific operations. Each image
     * format comes with its own implementation to retrieve metadata attributes such as
     * bit-depth, width, and height.
     */
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

        /**
         * Determines the image format based on the file extension of the provided image URL.
         *
         * @param imageUrl the URL or file path of the image, including its file extension
         * @return the corresponding ImageFormat enumeration value based on the file extension
         * @throws IllegalArgumentException if the file extension does not match any supported ImageFormat
         * @throws NullPointerException if imageUrl is null
         */
        static ImageFormat forImageUrl(String imageUrl) {
            return ImageFormat.valueOf(StringUtils.upperCase(StringUtils.substringAfterLast(imageUrl, ".")));
        }

        /**
         * Retrieves the ImageFormat enumeration value based on the provided metadata native format name.
         *
         * @param metadataNativeFormatName the native format name of the metadata to match against available ImageFormat values
         * @return the corresponding ImageFormat enumeration value if a match is found, otherwise null
         */
        static ImageFormat forMetadataNativeFormatName(String metadataNativeFormatName) {
            return Stream.of(ImageFormat.values()) //
                .filter(i -> StringUtils.equals(i.getMetadataNativeFormatName(), metadataNativeFormatName)) //
                .findAny() //
                .orElse(null);
        }

        /**
         * Retrieves the file extension associated with this image format.
         *
         * @return the file extension as a string, prefixed with a dot (e.g., ".png").
         */
        String getExtension() {
            return "." + getName();
        }

        /**
         * Retrieves the name of this image format in lowercase.
         *
         * @return the name of the image format as a lowercase string
         */
        String getName() {
            return name().toLowerCase();
        }

        /**
         * Retrieves an {@link ImageReader} instance for this image format.
         *
         * @return an ImageReader corresponding to this image format, obtained by matching the format name
         */
        ImageReader getImageReader() {
            return ImageIO.getImageReadersByFormatName(getName()).next();
        }

        /**
         * Retrieves an {@link ImageWriter} instance for this image format.
         *
         * @return an ImageWriter corresponding to this image format, obtained by matching the format name
         */
        ImageWriter getImageWriter() {
            return ImageIO.getImageWritersByFormatName(getName()).next();
        }

        /**
         * Retrieves the native metadata format name associated with this image format.
         *
         * @return the native metadata format name as a string.
         */
        abstract String getMetadataNativeFormatName();

        /**
         * Retrieves the metadata image information tag associated with the image format.
         *
         * This method provides the specific metadata tag used to identify or describe image
         * information within the metadata structure for the respective image format.
         *
         * @return the metadata image information tag as a string
         */
        abstract String getMetadataImageInfoTag();

        /**
         * Retrieves the metadata attribute name used to represent the bit depth
         * of an image in the respective image format.
         *
         * @return the metadata attribute name as a string, representing the bit depth of the image
         */
        abstract String getMetadataBitDepthAttribute();

        /**
         * Retrieves the metadata attribute name used to represent the width of an image
         * in the respective image format.
         *
         * @return the metadata attribute name as a string, representing the width of the image
         */
        abstract String getMetadataWidthAttribute();

        /**
         * Retrieves the metadata attribute name used to represent the height of an image
         * in the respective image format.
         *
         * @return the metadata attribute name as a string, representing the height of the image
         */
        abstract String getMetadataHeightAttribute();

    }

    /**
     * SaveMode defines the behavior for saving images, providing strategies to either save the image
     * in its original format or convert it to a specific format such as JPG. Each mode implements its
     * own logic for determining the target image format and saving the image to a specified file.
     */
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