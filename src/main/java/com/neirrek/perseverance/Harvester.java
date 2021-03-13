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

@Command(name = "perseverance-harvester", description = "Perseverance raw images harvester command")
public class Harvester {

    private static final String GECKO_DRIVER_PATH_PROPERTY = "webdriver.gecko.driver";

    private static final int DEFAULT_DOWNLOAD_THREADS_NUMBER = 4;

    private static final String RAW_IMAGES_URL = "https://mars.nasa.gov/mars2020/multimedia/raw-images/";

    private static final String IMAGE_URL_PATTERN = "^https:\\/\\/.+\\/pub\\/ods\\/surface\\/sol\\/(\\d{5})\\/ids\\/([a-z]+)\\/browse\\/([a-z]+)\\/(.+)$";

    private static final String IMAGE_PATH_PATTERN = "$1\\/$2\\/$3\\/$4";

    private static final String THUMBNAIL_IMAGES_SUFFIX = "_320.jpg";

    private static final String LARGE_IMAGES_SUFFIX = ".png";

    static {
        // Setting where the Gecko driver is on your system
        System.setProperty(GECKO_DRIVER_PATH_PROPERTY,
                System.getProperty(GECKO_DRIVER_PATH_PROPERTY, Config.getGeckoDriverPath()));
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
        boolean alreadyDone = true;
        try {
            for (int i = 0; i < imagesUrls.size(); i++) {
                alreadyDone &= !downloadImageService.take().get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new HarvesterException(String.format("An error occurred while downloading page %s", page),
                    e.getCause());
        }
        if (alreadyDone) {
            logger.info("Page already fully downloaded!");
        }
        printEndPage(page, nbPages);
        return alreadyDone;
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
        paginationInput = driver.findElement(By.id("header_pagination"));
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
                .map(e -> e.findElement(By.tagName("img")))
                .map(e -> StringUtils.replace(e.getAttribute("src"), THUMBNAIL_IMAGES_SUFFIX, LARGE_IMAGES_SUFFIX))
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
            String imagePath = String.format("%s%s%s", saveRootDirectory, File.separator,
                    RegExUtils.replacePattern(imageUrl, IMAGE_URL_PATTERN, IMAGE_PATH_PATTERN));
            File file = new File(imagePath);
            boolean downloaded = false;
            boolean toDownload = !file.exists() || force;
            if (logger.isInfoEnabled()) {
                logger.info(String.format("%s %s", toDownload ? "*" : " ", imageUrl));
            }
            if (toDownload) {
                FileUtils.forceMkdirParent(file);
                InputStream bodyStream = Jsoup.connect(imageUrl).ignoreContentType(true).maxBodySize(0).execute()
                        .bodyStream();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    IOUtils.copy(bodyStream, out);
                    nbDownloadedImages.getAndIncrement();
                    downloaded = true;
                } finally {
                    bodyStream.close();
                }
            }
            return downloaded;
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
                throw new HarvesterException("Unable to initialize de configuration", e);
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
