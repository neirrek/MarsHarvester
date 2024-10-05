package com.neirrek.perseverance;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
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
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.AirlineModule;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.ranges.IntegerRange;

/**
 * <p>
 * A class that downloads the raw images taken by the Mars rover Perseverance
 * that are available on the NASA website at the following URL: <a href=
 * "https://mars.nasa.gov/mars2020/multimedia/raw-images">https://mars.nasa.gov/mars2020/multimedia/raw-images</a>
 * </p>
 * <p>
 * This class can be executed as a command with the following parameters:
 * 
 * <pre>
 *  -d [saveRootDirectory], --dir [saveRootDirectory]
 *      Root directory in which the images are saved
 *      
 *  -f [fromPage], --fromPage [fromPage]
 *      Harvesting starts from this page
 *      
 *  --force
 *      Force harvesting already downloaded images
 *      
 *  -h, --help
 *      Display help information
 *      
 *  -s [n], --stop-at-already-downloaded-pages [n]
 *      Harvesting stops after the nth page which is already fully downloaded
 *      
 *  -t [toPage], --toPage [toPage]
 *      Harvesting stops at this page
 *      
 *  --threads [downloadThreadsNumber]
 *      Number of threads to download the images (default is 4)
 *      
 *  --convert-to-jpg [compression-ratio]
 *      Convert the downloaded images to JPG format with the given compression ratio
 * 
 * </pre>
 * </p>
 * 
 * @author Bruno Kerrien
 *
 */
@Command(name = "perseverance-harvester", description = "Perseverance raw images harvester command")
public class Harvester {

    private static final String GECKO_DRIVER_PATH_PROPERTY = "webdriver.gecko.driver";

    private static final String FIREFOX_BIN_PATH_PROPERTY = "webdriver.firefox.bin";

    private static final int DEFAULT_DOWNLOAD_THREADS_NUMBER = 4;

    private static final int DEFAULT_JPG_COMPRESSION_RATIO = 0;

    private static final int MAX_JPG_COMPRESSION_RATIO = 100;

    private static final String RAW_IMAGES_URL = "https://mars.nasa.gov/mars2020/multimedia/raw-images/";

    private static final String IMAGES_URL_PATTERN = "^https:\\/\\/.+\\/pub\\/ods\\/surface\\/sol\\/(\\d{5})\\/ids\\/([a-zA-Z]+)\\/browse\\/([a-zA-Z]+)\\/(.+)\\.png$";

    private static final String IMAGES_SAVE_PATH_PATTERN = "$1\\/$2\\/$3\\/$4%s";

    private static final String THUMBNAIL_IMAGES_SUFFIX = "_320.jpg";

    private static final String LARGE_IMAGES_SUFFIX = ImageFormat.PNG.getExtension();

    static {
        // If the JVM property "webdriver.gecko.driver", which defines
        // the path to the Gecko driver on your system, is not set then
        // setting it with the value defined in the config.properties file
        if (System.getProperty(GECKO_DRIVER_PATH_PROPERTY, null) == null) {
            System.setProperty(GECKO_DRIVER_PATH_PROPERTY, Config.getGeckoDriverPath());
        }
        // Redirecting the browser logs to /dev/null
        System.setProperty(GeckoDriverService.GECKO_DRIVER_LOG_PROPERTY, "/dev/null");
        // And disabling the useless Selenium logs not to pollute the logs
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
    }

    private final Logger logger = LoggerFactory.getLogger(Harvester.class);

    @Option(name = { "-d", "--dir" }, description = "Root directory in which the images are saved")
    private String saveRootDirectory;

    @Option(name = { "-f",
        "--fromPage" }, description = "Harvesting starts from this page (default is page 1 when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    private int fromPage = 1;

    @Option(name = { "-t",
        "--toPage" }, description = "Harvesting stops at this page (default is last page when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    private int toPage = Integer.MAX_VALUE;

    @Option(name = { "--force" }, description = "Force harvesting already downloaded images")
    private boolean force;

    @Option(name = { "-s",
        "--stop-after-already-downloaded-pages" }, description = "Harvesting stops after the nth page which is already fully downloaded (default is not to stop when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    private int stopAfterAlreadyDownloadedPages;

    @Option(name = {
        "--threads" }, description = "Number of threads to download the images (default is 4 when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    private int downloadThreadsNumber = DEFAULT_DOWNLOAD_THREADS_NUMBER;

    @Option(name = {
        "--convert-to-jpg" }, description = "Convert the downloaded images to JPG format with the given compression ratio (default is not to convert when this option is missing")
    @IntegerRange(min = 1, minInclusive = true, max = 100, maxInclusive = true)
    private int jpgCompressionRatio = DEFAULT_JPG_COMPRESSION_RATIO;

    @AirlineModule
    private HelpOption<Harvester> help;

    private CompletionService<Boolean> downloadImageService;

    private WebDriver driver;

    private WebElement paginationInput;

    private ImageFormat imagesSaveFormat = ImageFormat.PNG;

    private AtomicInteger nbDownloadedImages = new AtomicInteger(0);

    public static void main(String[] args) {
        SingleCommand<Harvester> parser = SingleCommand.singleCommand(Harvester.class);
        Harvester harvester = parser.parse(args);
        if (!harvester.showHelp()) {
            harvester.execute();
        }
    }

    private void execute() {
        initializeDriverAndPagination();
        if (jpgCompressionRatio < DEFAULT_JPG_COMPRESSION_RATIO) {
            jpgCompressionRatio = DEFAULT_JPG_COMPRESSION_RATIO;
        } else {
            imagesSaveFormat = ImageFormat.JPG;
            jpgCompressionRatio = Math.min(jpgCompressionRatio, MAX_JPG_COMPRESSION_RATIO);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(downloadThreadsNumber);
        downloadImageService = new ExecutorCompletionService<>(executorService);
        int maxPage = Math.min(getNumberOfPages(), toPage);
        boolean stop = false;
        int nbPagesAlreadyProcessed = 0;
        for (int p = fromPage; p <= maxPage && !stop; p++) {
            boolean alreadyProcessed = processPage(p, maxPage);
            if (alreadyProcessed) {
                stop = ++nbPagesAlreadyProcessed == stopAfterAlreadyDownloadedPages;
            } else {
                nbPagesAlreadyProcessed = 0;
            }
        }
        driver.quit();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%s images downloaded", nbDownloadedImages));
        }
        executorService.shutdown();
    }

    private boolean processPage(int page, int nbPages) {
        logStartPage(page, nbPages);
        goToPage(page);
        List<String> imagesUrls = getImagesUrls();
        for (String imageUrl : imagesUrls) {
            downloadImageService.submit(new ImageDownloader(imageUrl, saveRootDirectory, imagesSaveFormat,
                jpgCompressionRatio, force, nbDownloadedImages, logger));
        }
        boolean pageAlreadyDownloaded = true;
        try {
            for (int i = 0; i < imagesUrls.size(); i++) {
                pageAlreadyDownloaded &= !downloadImageService.take().get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new HarvesterException(String.format("An error occurred while downloading page %s", page),
                e.getCause());
        }
        if (pageAlreadyDownloaded) {
            logger.info("Page already fully downloaded!");
        }
        printEndPage(page, nbPages);
        return pageAlreadyDownloaded;
    }

    private void initializeDriverAndPagination() {
        if (driver != null) {
            driver.quit();
        }
        FirefoxOptions firefoxOptions = new FirefoxOptions();
        firefoxOptions.setBinary(Config.getFirefoxBinPath());
        firefoxOptions.addArguments("--headless", "--disable-gpu", "--window-size=2560,1440");
        driver = new FirefoxDriver(firefoxOptions);
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
        driver.get(RAW_IMAGES_URL);
        paginationInput = driver.findElement(By.id("header_pagination"));
    }

    private int getNumberOfPages() {
        return Integer.parseInt(paginationInput.getAttribute("max"));
    }

    private void goToPage(int page) {
        String startIndex = NumberFormat.getInstance(Locale.ENGLISH).format((page - 1) * 100 + 1L);
        boolean ok = false;
        int retry = 0;
        while (!ok && retry < 10) {
            scrollIntoView(paginationInput).clear();
            paginationInput.sendKeys(String.valueOf(page));
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.textToBePresentInElementLocated(By.className("start_index"), startIndex));
                ok = true;
            } catch (TimeoutException e) {
                retry++;
                logger.debug(e.getMessage(), e);
            }
        }
    }

    private List<String> getImagesUrls() {
        return new WebDriverWait(driver, Duration.ofSeconds(10)).ignoring(StaleElementReferenceException.class)
            .until(d -> d.findElements(By.className("raw_list_image_inner")).stream()
                .map(e -> e.findElement(By.tagName("img")))
                .map(e -> StringUtils.replace(e.getAttribute("src"), THUMBNAIL_IMAGES_SUFFIX, LARGE_IMAGES_SUFFIX))
                .collect(Collectors.toList()));
    }

    private void logStartPage(int page, int nbPages) {
        logPagePart("Start", page, nbPages);
    }

    private void printEndPage(int page, int nbPages) {
        logPagePart("End", page, nbPages);
    }

    private void logPagePart(String pagePart, int page, int nbPages) {
        if (logger.isInfoEnabled()) {
            String message = StringUtils.rightPad(String.format("====[%s of page %s/%s]", pagePart, page, nbPages), 148,
                "=");
            logger.info(message);
        }
    }

    private boolean showHelp() {
        boolean helpShown = help.showHelpIfRequested();
        if (!helpShown && saveRootDirectory == null) {
            help.showHelp();
            helpShown = true;
        }
        return helpShown;
    }

    private WebElement scrollIntoView(WebElement webElement) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", webElement);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return webElement;
    }

    private static class ImageDownloader implements Callable<Boolean> {

        private final String imageUrl;

        private final String saveRootDirectory;

        private final ImageFormat imagesSaveFormat;

        private final float compressionQuality;

        private final boolean force;

        private final AtomicInteger nbDownloadedImages;

        private final Logger logger;

        public ImageDownloader(String imageUrl, String saveRootDirectory, ImageFormat imagesSaveFormat,
            int jpgConversionRatio, boolean force, AtomicInteger nbDownloadedImages, Logger logger) {
            this.imageUrl = imageUrl;
            this.saveRootDirectory = saveRootDirectory;
            this.imagesSaveFormat = imagesSaveFormat;
            this.compressionQuality = (float) jpgConversionRatio / 100;
            this.force = force;
            this.nbDownloadedImages = nbDownloadedImages;
            this.logger = logger;
        }

        @Override
        public Boolean call() throws Exception {
            String imagePath = String.format("%s%s%s", saveRootDirectory, File.separator,
                RegExUtils.replacePattern(imageUrl, IMAGES_URL_PATTERN, imagesSaveFormat.getImagesSavePathPattern()));
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
                        imagesSaveFormat.saveImage(bodyStream, file, this);
                        nbDownloadedImages.getAndIncrement();
                        downloaded = true;
                    } catch (IOException e) {
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

        private void savePNGImageToFile(InputStream pngImageInputStream, File pngFile) throws IOException {
            try (FileOutputStream fileOutputStream = new FileOutputStream(pngFile)) {
                IOUtils.copy(pngImageInputStream, fileOutputStream);
            }
        }

        private void convertPNGImageToJPG(InputStream pngImageInputStream, File jpgFile) throws IOException {
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

    }

    private enum ImageFormat {

        PNG {
            @Override
            void saveImage(InputStream imageInputStream, File imageFile, ImageDownloader imageDownloader)
                throws IOException {
                imageDownloader.savePNGImageToFile(imageInputStream, imageFile);
            }

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
            void saveImage(InputStream imageInputStream, File imageFile, ImageDownloader imageDownloader)
                throws IOException {
                imageDownloader.convertPNGImageToJPG(imageInputStream, imageFile);
            }

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

        private final String imagesSavePathPattern = String.format(IMAGES_SAVE_PATH_PATTERN, getExtension());

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

        String getImagesSavePathPattern() {
            return imagesSavePathPattern;
        }

        ImageReader getImageReader() {
            return ImageIO.getImageReadersByFormatName(getName()).next();
        }

        ImageWriter getImageWriter() {
            return ImageIO.getImageWritersByFormatName(getName()).next();
        }

        abstract void saveImage(InputStream imageInputStream, File imageFile, ImageDownloader imageDownloader)
            throws IOException;

        abstract String getMetadataNativeFormatName();

        abstract String getMetadataImageInfoTag();

        abstract String getMetadataBitDepthAttribute();

        abstract String getMetadataWidthAttribute();

        abstract String getMetadataHeightAttribute();

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

    private static class Config {

        private static Config instance;

        private final Properties properties = new Properties();

        private Config() {
            try {
                properties.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
            } catch (IOException | IllegalArgumentException | NullPointerException e) {
                throw new HarvesterException("Unable to load the properties", e);
            }
        }

        private static Config getInstance() {
            if (instance == null) {
                instance = new Config();
            }
            return instance;
        }

        static String getGeckoDriverPath() {
            return getInstance().getProperty(GECKO_DRIVER_PATH_PROPERTY);
        }

        static String getFirefoxBinPath() {
            return getInstance().getProperty(FIREFOX_BIN_PATH_PROPERTY);
        }

        private String getProperty(String propertyName) {
            return properties.getProperty(propertyName);
        }

    }

    public static class HarvesterException extends RuntimeException {

        private static final long serialVersionUID = -1569660223288867827L;

        public HarvesterException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
