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
package com.neirrek.harvester.exception;

import java.io.Serial;

/**
 * Custom exception class used within harvesting operations to handle errors
 * specific to processing and configuration tasks.
 *
 * This exception extends {@link RuntimeException}, enabling unchecked exception
 * handling for runtime issues such as failures in configuration loading, page
 * navigation, or concurrent image downloading tasks.
 *
 * It can encapsulate a detailed error message and the original cause of the exception,
 * providing better traceability for underlying issues during harvesting operations.
 */
public class HarvesterException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 5443873764986661772L;

    public HarvesterException(String message, Throwable cause) {
        super(message, cause);
    }

}
