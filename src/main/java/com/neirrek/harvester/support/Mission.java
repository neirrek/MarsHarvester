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

    private Mission(String rawImagesUrl, PatternsMappings imageUrlPathMappings,
        PatternsMappings thumbnailLargeImagesUrlMappings) {
        this.rawImagesUrl = rawImagesUrl;
        this.imageUrlPathMappings = imageUrlPathMappings;
        this.thumbnailLargeImagesUrlMappings = thumbnailLargeImagesUrlMappings;
    }

    public String getRawImagesUrl() {
        return rawImagesUrl;
    }

    public String largeImageUrl(String thumbnailUrl) {
        return thumbnailLargeImagesUrlMappings.largeImageUrl(thumbnailUrl);

    }

    String imagePath(String imageUrl, String saveRootDirectory, ImageFormat saveImageFormat) {
        return imageUrlPathMappings.imagePath(imageUrl, saveRootDirectory, saveImageFormat);
    }

    public abstract WebElement getPaginationInput(WebDriver driver);

    public abstract int getNbImagesPerPage();

    abstract String alternateImageUrl(String imageUrl);

    private static class PatternsMappings {

        private final Map<String, String> patternsMappings;

        public PatternsMappings(Map<String, String> patternsMappings) {
            this.patternsMappings = patternsMappings;
        }

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