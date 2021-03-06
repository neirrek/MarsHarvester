package com.neirrek.perseverance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
import org.yaml.snakeyaml.Yaml;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import com.machinepublishers.jbrowserdriver.UserAgent;

public class Harvester {

    private static final Settings DRIVER_SETTINGS = Settings.builder().screen(new Dimension(1920, 1080))
            .timezone(Timezone.EUROPE_PARIS).quickRender(true).headless(true).userAgent(UserAgent.CHROME)
            .loggerLevel(Level.OFF).build();

    private static final String IMAGE_URL_PATTERN = "^https:\\/\\/.+\\/pub\\/ods\\/surface\\/sol\\/(\\d{5})\\/ids\\/([a-z]+)\\/browse\\/([a-z]+)\\/(.+)$";

    private static final String IMAGE_PATH_PATTERN = "$1\\/$2\\/$3\\/$4";

    private final Logger logger = LoggerFactory.getLogger(Harvester.class);

    private JBrowserDriver driver;

    public Harvester() {
        initializeDriver();
    }

    public static void main(String[] args) throws Exception {
        new Harvester().execute();
    }

    private void execute() throws Exception {
        WebElement paginationInput = driver.findElement(By.id("header_pagination"));
        int nbPages = Integer.parseInt(paginationInput.getAttribute("max"));
        int nbImages = 0;
        boolean done = false;
        for (int p = 1; p <= nbPages && !done; p++) {
            printStartPage(p, nbPages);
            done = true;
            paginationInput.clear();
            paginationInput.sendKeys(String.valueOf(p));
            driver.pageWait();
            List<WebElement> thumbnails = driver.findElements(By.className("raw_list_image_inner"));
            for (WebElement thumbnail : thumbnails) {
                String imageUrl = StringUtils.replace(thumbnail.findElement(By.tagName("img")).getAttribute("src"),
                        "_320.jpg", ".png");
                if (saveImage(imageUrl)) {
                    done = false;
                    nbImages++;
                }
            }
            if (done) {
                logger.info("Page already fully downloaded!");
            }
            printEndPage(p, nbPages);
            if (!done) {
                // The driver is re-initialized between each page
                // to avoid it being stuck after a few pages
                initializeDriver();
                paginationInput = driver.findElement(By.id("header_pagination"));
            }
        }
        driver.quit();
        if (logger.isInfoEnabled()) {
            logger.info(String.format("%s images downloaded", nbImages));
        }
    }

    private void initializeDriver() {
        if (driver != null) {
            driver.quit();
        }
        driver = new JBrowserDriver(DRIVER_SETTINGS);
        driver.get(Config.rawImagesUrl());
        driver.pageWait();
    }

    private boolean saveImage(String imageUrl) throws Exception {
        String imagePath = String.format("%s/%s", Config.saveRootDirectory(),
                RegExUtils.replacePattern(imageUrl, IMAGE_URL_PATTERN, IMAGE_PATH_PATTERN));
        File file = new File(imagePath);
        boolean downloaded = false;
        if (!file.exists()) {
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
        }
        return downloaded;
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

    private enum PagePart {
        START, END;
    }

    private static class Config {

        private static Map<String, String> values;

        static {
            load();
        }

        static String rawImagesUrl() {
            return values.get("rawImagesUrl");
        }

        static String saveRootDirectory() {
            return values.get("saveRootDirectory");
        }

        private static void load() {
            Yaml yaml = new Yaml();
            InputStream inputStream = Config.class.getClassLoader().getResourceAsStream("config.yml");
            values = yaml.load(inputStream);
        }

    }

}
