package com.neirrek.harvester;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.AirlineModule;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.AllowedValues;
import com.github.rvesse.airline.annotations.restrictions.Once;
import com.github.rvesse.airline.annotations.restrictions.Path;
import com.github.rvesse.airline.annotations.restrictions.PathKind;
import com.github.rvesse.airline.annotations.restrictions.ranges.IntegerRange;
import com.neirrek.harvester.configuration.Config;
import com.neirrek.harvester.exception.HarvesterException;
import com.neirrek.harvester.support.ImageDownloader;
import com.neirrek.harvester.support.ImageDownloader.SaveMode;
import com.neirrek.harvester.support.Mission;

/**
 * <p>
 * A class that downloads the raw images taken by the Mars rovers Perseverance
 * and Curiosity that are available on the NASA website at the following URLs: -
 * Perseverance: <a href=
 * "https://mars.nasa.gov/mars2020/multimedia/raw-images/">https://mars.nasa.gov/mars2020/multimedia/raw-images/</a>
 * </p>
 * - Curiosity: <a href=
 * "https://mars.nasa.gov/msl/multimedia/raw-images/">https://mars.nasa.gov/msl/multimedia/raw-images/</a>
 * <p>
 * This class can be executed as a command with the following parameters:
 * 
 * <pre>
 *  -m [mission], --mission [mission]
 *      Name of the Mars mission (CURIOSITY or PERSEVERANCE)
 *       
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
@Command(name = "mars-harvester", description = "Mars rovers raw images harvester command")
public class MarsHarvester {

    private static final int DEFAULT_DOWNLOAD_THREADS_NUMBER = 4;

    private static final int DEFAULT_JPG_COMPRESSION_RATIO = 0;

    private static final int MAX_JPG_COMPRESSION_RATIO = 100;

    static {
        Config.initialize();
    }

    private final Logger logger = LoggerFactory.getLogger(MarsHarvester.class);

    @Option(name = { "-m", "--mission" }, arity = 1, description = "Name of the Mars mission")
    @AllowedValues(allowedValues = { "CURIOSITY", "PERSEVERANCE" })
    @Once
    private Mission mission;

    @Option(name = { "-d", "--dir" }, arity = 1, description = "Root directory in which the images are saved")
    @Path(mustExist = true, kind = PathKind.DIRECTORY)
    @Once
    private String saveRootDirectory;

    @Option(name = { "-f",
        "--fromPage" }, arity = 1, description = "Harvesting starts from this page (default is page 1 when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    @Once
    private int fromPage = 1;

    @Option(name = { "-t",
        "--toPage" }, arity = 1, description = "Harvesting stops at this page (default is last page when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    @Once
    private int toPage = Integer.MAX_VALUE;

    @Option(name = { "--force" }, description = "Force harvesting already downloaded images")
    @Once
    private boolean force;

    @Option(name = { "-s",
        "--stop-after-already-downloaded-pages" }, arity = 1, description = "Harvesting stops after the nth page which is already fully downloaded (default is not to stop when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    @Once
    private int stopAfterAlreadyDownloadedPages;

    @Option(name = {
        "--threads" }, arity = 1, description = "Number of threads to download the images (default is 4 when this option is missing)")
    @IntegerRange(min = 1, minInclusive = true)
    @Once
    private int downloadThreadsNumber = DEFAULT_DOWNLOAD_THREADS_NUMBER;

    @Option(name = {
        "--convert-to-jpg" }, arity = 1, description = "Convert the downloaded images to JPG format with the given compression ratio (default is not to convert when this option is missing")
    @IntegerRange(min = 1, minInclusive = true, max = 100, maxInclusive = true)
    @Once
    private int jpgCompressionRatio = DEFAULT_JPG_COMPRESSION_RATIO;

    @AirlineModule
    private HelpOption<MarsHarvester> help;

    private CompletionService<Boolean> downloadImageService;

    private WebDriver driver;

    private WebElement paginationInput;

    private SaveMode saveMode = SaveMode.AS_IS;

    private AtomicInteger nbDownloadedImages = new AtomicInteger(0);

    public static void main(String[] args) {
        SingleCommand<MarsHarvester> parser = SingleCommand.singleCommand(MarsHarvester.class);
        MarsHarvester harvester = parser.parse(args);
        if (!harvester.showHelp()) {
            harvester.execute();
        }
    }

    private void execute() {
        if (jpgCompressionRatio <= DEFAULT_JPG_COMPRESSION_RATIO) {
            jpgCompressionRatio = DEFAULT_JPG_COMPRESSION_RATIO;
        } else {
            saveMode = SaveMode.CONVERT_TO_JPG;
            jpgCompressionRatio = Math.min(jpgCompressionRatio, MAX_JPG_COMPRESSION_RATIO);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(downloadThreadsNumber);
        downloadImageService = new ExecutorCompletionService<>(executorService);
        int maxPage = 0;
        while (maxPage == 0) {
            initializeDriverAndPagination();
            maxPage = Math.min(getNumberOfPages(), toPage);
        }
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
            downloadImageService.submit(new ImageDownloader(mission, imageUrl, saveRootDirectory, saveMode,
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
        driver.get(mission.getRawImagesUrl());
        paginationInput = mission.getPaginationInput(driver);
    }

    private int getNumberOfPages() {
        return Integer.parseInt(paginationInput.getAttribute("max"));
    }

    private void goToPage(int page) {
        String startIndex = NumberFormat.getInstance(Locale.ENGLISH)
            .format((page - 1) * mission.getNbImagesPerPage() + 1L);
        boolean ok = false;
        int retry = 0;
        WebDriverException exception = null;
        while (!ok && retry < 10) {
            scrollIntoView(paginationInput).clear();
            paginationInput.sendKeys(String.valueOf(page));
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.textToBePresentInElementLocated(By.className("start_index"), startIndex));
                exception = null;
                ok = true;
            } catch (TimeoutException e) {
                exception = e;
                retry++;
                logger.debug(e.getMessage(), e);
            }
        }
        if (exception != null) {
            throw new HarvesterException(String.format("An error occurred while going to page %s", page), exception);
        }
    }

    private List<String> getImagesUrls() {
        return new WebDriverWait(driver, Duration.ofSeconds(10)).ignoring(StaleElementReferenceException.class)
            .until(d -> d.findElements(By.className("raw_list_image_inner")).stream() //
                .map(e -> e.findElement(By.tagName("img")).getAttribute("src")) //
                .map(s -> mission.largeImageUrl(s)) //
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

}
