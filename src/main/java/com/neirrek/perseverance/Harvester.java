package com.neirrek.perseverance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import com.machinepublishers.jbrowserdriver.UserAgent;

@Command(name = "perseverance-harvester", description = "Perseverance raw images harvester command")
public class Harvester {

    private static final String RAW_IMAGES_URL = "https://mars.nasa.gov/mars2020/multimedia/raw-images/";

    private static final Settings DRIVER_SETTINGS = Settings.builder().screen(new Dimension(1920, 1080))
            .timezone(Timezone.EUROPE_PARIS).quickRender(true).headless(true).userAgent(UserAgent.CHROME).ajaxWait(300)
            .loggerLevel(Level.OFF).build();

    private static final String IMAGE_URL_PATTERN = "^https:\\/\\/.+\\/pub\\/ods\\/surface\\/sol\\/(\\d{5})\\/ids\\/([a-z]+)\\/browse\\/([a-z]+)\\/(.+)$";

    private static final String IMAGE_PATH_PATTERN = "$1\\/$2\\/$3\\/$4";

    private final Logger logger = LoggerFactory.getLogger(Harvester.class);

    @Option(name = { "-d", "--dir" }, description = "Root directory in which the images are saved")
    private String saveRootDirectory;

    @Option(name = { "-f", "--fromPage" }, description = "Harvesting starts from this page")
    private int fromPage = 1;

    @Option(name = { "-t", "--toPage" }, description = "Harvesting stops at this page")
    private int toPage = Integer.MAX_VALUE;

    @Option(name = { "--force" }, description = "Force harvesting already downloaded images")
    private boolean force;

    @Inject
    private HelpOption<Harvester> help;

    private JBrowserDriver driver;

    private WebElement paginationInput;

    private int nbImages;

    public static void main(String[] args) {
        SingleCommand<Harvester> parser = SingleCommand.singleCommand(Harvester.class);
        Harvester cmd = parser.parse(args);
        if (!cmd.showHelp()) {
            cmd.execute();
        }
    }

    private void execute() {
        initializeDriverAndPagination();
        int maxPage = Math.min(Integer.parseInt(paginationInput.getAttribute("max")), toPage);
        boolean done = false;
        for (int p = fromPage; p <= maxPage && !done; p++) {
            done = processPage(p, maxPage);
            if (!done) {
                // The driver is re-initialized between each page
                // to avoid it being stuck after a few pages
                initializeDriverAndPagination();
            }
        }
        driver.quit();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%s images downloaded", nbImages));
        }
    }

    private boolean processPage(int page, int nbPages) {
        boolean done = true;
        printStartPage(page, nbPages);
        paginationInput.clear();
        paginationInput.sendKeys(String.valueOf(page));
        driver.pageWait();
        List<WebElement> thumbnails = driver.findElements(By.className("raw_list_image_inner"));
        for (WebElement thumbnail : thumbnails) {
            String imageUrl = StringUtils.replace(thumbnail.findElement(By.tagName("img")).getAttribute("src"),
                    "_320.jpg", ".png");
            if (downloadImage(imageUrl)) {
                done = false;
                nbImages++;
            }
        }
        if (done) {
            logger.info("Page already fully downloaded!");
        }
        printEndPage(page, nbPages);
        return done;
    }

    private boolean downloadImage(String imageUrl) {
        String imagePath = String.format("%s%s%s", saveRootDirectory, File.separator,
                RegExUtils.replacePattern(imageUrl, IMAGE_URL_PATTERN, IMAGE_PATH_PATTERN));
        File file = new File(imagePath);
        boolean downloaded = false;
        if (!file.exists() || force) {
            try {
                FileUtils.forceMkdirParent(file);
                InputStream bodyStream = Jsoup.connect(imageUrl).ignoreContentType(true).maxBodySize(0).execute()
                        .bodyStream();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    logger.info(imageUrl);
                    IOUtils.copy(bodyStream, out);
                    downloaded = true;
                } finally {
                    bodyStream.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(String.format("An error occurred while downloading image %s", imageUrl), e);
            }
        }
        return downloaded;
    }

    private void initializeDriverAndPagination() {
        if (driver != null) {
            driver.quit();
        }
        driver = new JBrowserDriver(DRIVER_SETTINGS);
        driver.get(RAW_IMAGES_URL);
        driver.pageWait();
        paginationInput = driver.findElement(By.id("header_pagination"));
    }

    private void printStartPage(int page, int nbPages) {
        printPagePart(PagePart.START, page, nbPages);
    }

    private void printEndPage(int page, int nbPages) {
        printPagePart(PagePart.END, page, nbPages);
    }

    private void printPagePart(PagePart pagePart, int page, int nbPages) {
        if (logger.isInfoEnabled()) {
            String message = StringUtils.rightPad(String.format("====[%s of page %s/%s]",
                    StringUtils.capitalize(pagePart.name().toLowerCase()), page, nbPages), 146, "=");
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

    private enum PagePart {
        START, END;
    }

}
