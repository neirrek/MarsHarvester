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

import static java.util.Map.entry;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.RegExUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.neirrek.harvester.support.ImageDownloader.ImageFormat;

/**
 * Enumeration representing different Mars missions and their associated configurations for handling
 * raw image URLs, thumbnail URL mappings, and large image URL mappings.
 * Each mission has specific methods for accessing pagination, image count per page, and alternate
 * image URL handling.
 *
 * @author Bruno Kerrien
 *
 */
public enum Mission {

    CURIOSITY( //
        "https://mars.nasa.gov/msl/multimedia/raw-images/", //
        PatternsMappings.ofEntries( //
            entry( //
                "^https:\\/\\/.+\\/msss\\/(\\d{5})\\/([a-zA-Z]+)\\/(.+)\\.(jpg|JPG|png|PNG)$", //
                "$1\\/$2\\/$3%s"), //
            entry( //
                "^https:\\/\\/.+(?:\\/proj\\/msl\\/redops)?\\/ods\\/surface\\/sol\\/(\\d{5})\\/([a-zA-Z]+)\\/([a-zA-Z]+)\\/([a-zA-Z]+)\\/(.+)\\.(jpg|JPG|png|PNG)$", //
                "$1\\/$2\\/$3\\/$4\\/$5%s")), //
        PatternsMappings.ofEntries( //
            entry( //
                "^(.+)-thm\\.jpg$", //
                "$1.JPG"), //
            entry( //
                "^(.+)\\.PNG$", "$1.PNG"))) {
        @Override
        public WebElement getPaginationInput(WebDriver driver) {
            return driver.findElement(By.cssSelector("div#primary_column input.page_num"));
        }

        @Override
        public int getNbImagesPerPage() {
            return 50;
        }

        @Override
        String alternateImageUrl(String imageUrl) {
            String urlJpgUpperCase = RegExUtils.replacePattern(imageUrl, "^(.+)\\.jpg$", "$1.JPG");
            return imageUrl.equals(urlJpgUpperCase) ? RegExUtils.replacePattern(imageUrl, "^(.+)\\.JPG$", "$1.jpg")
                : urlJpgUpperCase;
        }
    }, //
    PERSEVERANCE( //
        "https://mars.nasa.gov/mars2020/multimedia/raw-images/", //
        PatternsMappings.ofEntries( //
            entry( //
                "^https:\\/\\/.+\\/pub\\/ods\\/surface\\/sol\\/(\\d{5})\\/ids\\/([a-zA-Z]+)\\/browse\\/([a-zA-Z]+)\\/(.+)\\.png$", //
                "$1\\/$2\\/$3\\/$4%s")), //
        PatternsMappings.ofEntries( //
            entry( //
                "^(.+)_320\\.jpg$", //
                "$1.png"))) {
        @Override
        public WebElement getPaginationInput(WebDriver driver) {
            return driver.findElement(By.id("header_pagination"));
        }

        @Override
        public int getNbImagesPerPage() {
            return 100;
        }

        @Override
        String alternateImageUrl(String imageUrl) {
            return imageUrl;
        }
    };

    private final String rawImagesUrl;

    private final PatternsMappings imageUrlPathMappings;

    private final PatternsMappings thumbnailLargeImagesUrlMappings;

    Mission(String rawImagesUrl, PatternsMappings imageUrlPathMappings,
        PatternsMappings thumbnailLargeImagesUrlMappings) {
        this.rawImagesUrl = rawImagesUrl;
        this.imageUrlPathMappings = imageUrlPathMappings;
        this.thumbnailLargeImagesUrlMappings = thumbnailLargeImagesUrlMappings;
    }

    /**
     * Retrieves the base URL for accessing raw images associated with the mission.
     *
     * @return A String representing the URL used to fetch raw images.
     */
    public String getRawImagesUrl() {
        return rawImagesUrl;
    }

    /**
     * Converts a given thumbnail URL into its corresponding large image URL.
     *
     * @param thumbnailUrl The URL of the thumbnail image to be converted.
     * @return A String representing the URL of the large image corresponding to the provided thumbnail URL.
     * @throws RuntimeException if no matching pattern is found for the given thumbnail URL.
     */
    public String largeImageUrl(String thumbnailUrl) {
        return thumbnailLargeImagesUrlMappings.largeImageUrl(thumbnailUrl);

    }

    /**
     * Generates the file system path where an image, specified by its URL, should be saved.
     * The method maps the URL to a corresponding file path based on predefined patterns
     * and appends the appropriate file extension based on the specified image format.
     *
     * @param imageUrl The URL of the image for which the file path should be generated.
     * @param saveRootDirectory The root directory where the generated file path should be located.
     * @param saveImageFormat The format in which the image will be saved, determining the file extension.
     * @return A String representing the full file path for saving the image.
     * @throws RuntimeException If no matching pattern is found for the given image URL.
     */
    String imagePath(String imageUrl, String saveRootDirectory, ImageFormat saveImageFormat) {
        return imageUrlPathMappings.imagePath(imageUrl, saveRootDirectory, saveImageFormat);
    }

    /**
     * Retrieves the pagination input WebElement on a web page.
     *
     * @param driver The WebDriver instance used to interact with the web page.
     * @return A WebElement representing the pagination input field.
     */
    public abstract WebElement getPaginationInput(WebDriver driver);

    /**
     * Retrieves the number of images per page associated with the mission.
     *
     * @return An integer representing the number of images displayed per page.
     */
    public abstract int getNbImagesPerPage();

    /**
     * Converts the given image URL to an alternate URL based on predefined mappings or rules.
     *
     * @param imageUrl The original URL of the image that needs to be converted to an alternate URL.
     * @return A String representing the alternate URL for the given image URL.
     */
    abstract String alternateImageUrl(String imageUrl);

    private record PatternsMappings(Map<String, String> patternsMappings) {

        @SafeVarargs
        static PatternsMappings ofEntries(Entry<String, String>... entries) {
            return new PatternsMappings(Map.ofEntries(entries));
        }

        String imagePath(String imageUrl, String saveRootDirectory, ImageFormat saveImageFormat) {
            Entry<String, String> patternsMapping = patternsMappings.entrySet().stream() //
                .filter(e -> imageUrl.matches(e.getKey())) //
                .findFirst() //
                .orElseThrow(() -> new RuntimeException(
                    String.format("No matching pattern found for image URL '%s'", imageUrl)));
            return String.format("%s%s%s", saveRootDirectory, File.separator, RegExUtils.replacePattern(imageUrl,
                patternsMapping.getKey(), String.format(patternsMapping.getValue(), saveImageFormat.getExtension())));
        }

        String largeImageUrl(String thumbnailUrl) {
            Entry<String, String> patternsMapping = patternsMappings.entrySet().stream() //
                .filter(e -> thumbnailUrl.matches(e.getKey())) //
                .findFirst() //
                .orElseThrow(() -> new RuntimeException(
                    String.format("No matching pattern found for thumbnail URL '%s'", thumbnailUrl)));
            return RegExUtils.replacePattern(thumbnailUrl, patternsMapping.getKey(), patternsMapping.getValue());
        }

    }

}