package com.neirrek.harvester.configuration;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;

import org.openqa.selenium.firefox.GeckoDriverService;

import com.neirrek.harvester.exception.HarvesterException;

public class Config {

    private static final String GECKO_DRIVER_PATH_PROPERTY = "webdriver.gecko.driver";

    private static final String FIREFOX_BIN_PATH_PROPERTY = "webdriver.firefox.bin";

    private static final String SLF4J_INTERNAL_VERBOSITY_PROPERTY = "slf4j.internal.verbosity";

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

    public static void initialize() {
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
        // And setting SLF4J internal verbosity to WARN to avoid useless logs
        System.setProperty(SLF4J_INTERNAL_VERBOSITY_PROPERTY, "WARN");
    }

    static String getGeckoDriverPath() {
        return getInstance().getProperty(GECKO_DRIVER_PATH_PROPERTY);
    }

    public static String getFirefoxBinPath() {
        return getInstance().getProperty(FIREFOX_BIN_PATH_PROPERTY);
    }

    private String getProperty(String propertyName) {
        return properties.getProperty(propertyName);
    }

}
