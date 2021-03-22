package com.neirrek.perseverance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

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
 *  --stop-at-already-downloaded-page
 *      Harvesting stops at the first page which is already fully downloaded
 *      
 *  -t [toPage], --toPage [toPage]
 *      Harvesting stops at this page
 *      
 *  --threads [downloadThreadsNumber]
 *      Number of threads to download the images (default is 4)
 * </pre>
 * </p>
 * 
 * @author Bruno Kerrien
 *
 */
@Command(name = "perseverance-harvester", description = "Perseverance raw images harvester command")
public class Harvester {

    private static final String GECKO_DRIVER_PATH_PROPERTY = "webdriver.gecko.driver";

    private static final int DEFAULT_DOWNLOAD_THREADS_NUMBER = 5;

    private static final String RAW_IMAGES_URL = "https://mars.nasa.gov/msl/multimedia/raw-images/";

    private static final String IMAGE_URL_PATTERN_1 = "^https:\\/\\/.+\\/msss\\/(\\d{5})\\/([a-zA-Z]+)\\/(.+)$"; // https://mars.nasa.gov/msl-raw-images/msss/03062/mcam/3062MR0159910230206068C00_DXXX.jpg

    private static final String IMAGE_URL_PATTERN_2 = "^https:\\/\\/.+\\/proj\\/msl\\/redops\\/ods\\/surface\\/sol\\/(\\d{5})\\/([a-zA-Z]+)\\/([a-zA-Z]+)\\/([a-zA-Z]+)\\/(.+)$"; // https://mars.nasa.gov/msl-raw-images/proj/msl/redops/ods/surface/sol/03063/opgs/edr/ncam/NRB_669427196EDR_S0870792NCAM00567M_.JPG

    private static final String IMAGE_PATH_PATTERN_1 = "$1\\/$2\\/$3";

    private static final String IMAGE_PATH_PATTERN_2 = "$1\\/$2\\/$3\\/$4\\/$5";

    private static final String THUMBNAIL_IMAGES_PATTERN_1 = "^(.+)-thm\\.jpg$";

    private static final String LARGE_IMAGES_PATTERN_1 = "$1.JPG";

    static {
        // If the JVM property "webdriver.gecko.driver", which defines
        // the path to the Gecko driver on your system, is not set then
        // setting it with the value defined in the config.properties file
        if (System.getProperty(GECKO_DRIVER_PATH_PROPERTY, null) == null) {
            System.setProperty(GECKO_DRIVER_PATH_PROPERTY, Config.getGeckoDriverPath());
        }
        // Redirecting the browser logs to /dev/null
        System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null");
        // And disabling the useless Selenium logs not to pollute the logs
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
    }

    private final Logger logger = LoggerFactory.getLogger(Harvester.class);

    @Option(name = { "-d", "--dir" }, description = "Root directory in which the images are saved")
    private String saveRootDirectory;

    @Option(name = { "-f", "--fromPage" }, description = "Harvesting starts from this page")
    private int fromPage = 1;

    @Option(name = { "-t", "--toPage" }, description = "Harvesting stops at this page")
    private int toPage = Integer.MAX_VALUE;

    @Option(name = { "--force" }, description = "Force harvesting already downloaded images")
    private boolean force;

    @Option(name = {
            "--stop-at-already-downloaded-page" }, description = "Harvesting stops at the first page which is already fully downloaded")
    private boolean stopAtAlreadyDownloadedPage;

    @Option(name = { "--threads" }, description = "Number of threads to download the images (default is 4)")
    private int downloadThreadsNumber = DEFAULT_DOWNLOAD_THREADS_NUMBER;

    @Inject
    private HelpOption<Harvester> help;

    private CompletionService<Boolean> downloadImageService;

    private WebDriver driver;

    private WebElement paginationInput;

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
        ExecutorService executorService = Executors.newFixedThreadPool(downloadThreadsNumber);
        downloadImageService = new ExecutorCompletionService<>(executorService);
        int maxPage = Math.min(getNumberOfPages(), toPage);
        boolean stop = false;
        for (int p = fromPage; p <= maxPage && !stop; p++) {
            stop = processPage(p, maxPage) && stopAtAlreadyDownloadedPage;
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
            downloadImageService.submit(new DownloadImageCallable(imageUrl));
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
        firefoxOptions.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200");
        driver = new FirefoxDriver(firefoxOptions);
        driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
        driver.get(RAW_IMAGES_URL);
        paginationInput = driver.findElement(By.cssSelector("div#primary_column input.page_num"));
    }

    private int getNumberOfPages() {
        return Integer.parseInt(paginationInput.getAttribute("max"));
    }

    private void goToPage(int page) {
        String startIndex = NumberFormat.getInstance(Locale.ENGLISH).format((page - 1) * 50 + 1L);
        boolean ok = false;
        int retry = 0;
        while (!ok && retry < 10) {
            paginationInput.clear();
            paginationInput.sendKeys(String.valueOf(page));
            try {
                new WebDriverWait(driver, 10).until(
                        ExpectedConditions.textToBePresentInElementLocated(By.className("start_index"), startIndex));
                ok = true;
            } catch (TimeoutException e) {
                retry++;
                logger.debug(e.getMessage(), e);
            }
        }
    }

    private List<String> getImagesUrls() {
        return driver.findElements(By.className("raw_list_image_inner")).stream()
                .map(e -> e.findElement(By.tagName("img"))).map(e -> RegExUtils.replacePattern(e.getAttribute("src"),
                        THUMBNAIL_IMAGES_PATTERN_1, LARGE_IMAGES_PATTERN_1))
                .collect(Collectors.toList());
    }

    private void logStartPage(int page, int nbPages) {
        logPagePart(PagePart.START, page, nbPages);
    }

    private void printEndPage(int page, int nbPages) {
        logPagePart(PagePart.END, page, nbPages);
    }

    private void logPagePart(PagePart pagePart, int page, int nbPages) {
        if (logger.isInfoEnabled()) {
            String message = StringUtils.rightPad(String.format("====[%s of page %s/%s]",
                    StringUtils.capitalize(pagePart.name().toLowerCase()), page, nbPages), 148, "=");
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

    private class DownloadImageCallable implements Callable<Boolean> {

        private String imageUrl;

        public DownloadImageCallable(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        @Override
        public Boolean call() throws Exception {
            String imagePath;
            if (imageUrl.matches(IMAGE_URL_PATTERN_1)) {
                imagePath = String.format("%s%s%s", saveRootDirectory, File.separator,
                        RegExUtils.replacePattern(imageUrl, IMAGE_URL_PATTERN_1, IMAGE_PATH_PATTERN_1));
            } else if (imageUrl.matches(IMAGE_URL_PATTERN_2)) {
                imagePath = String.format("%s%s%s", saveRootDirectory, File.separator,
                        RegExUtils.replacePattern(imageUrl, IMAGE_URL_PATTERN_2, IMAGE_PATH_PATTERN_2));
            } else {
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
                    logImageDownload(imageUrl, toDownload, retry);
                    InputStream bodyStream = null;
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        bodyStream = Jsoup.connect(imageUrl).ignoreContentType(true).maxBodySize(0).execute()
                                .bodyStream();
                        IOUtils.copy(bodyStream, out);
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
                logImageDownload(imagePath, toDownload, 0);
            }
            return downloaded;
        }

        private void logImageDownload(String imageUrl, boolean toDownload, int retry) {
            if (logger.isInfoEnabled()) {
                String bullet = " ";
                if (toDownload) {
                    bullet = retry > 0 ? "!" : "*";
                }
                logger.info(String.format("%s %s", bullet, imageUrl));
            }
        }

    }

    private enum PagePart {
        START, END;
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
