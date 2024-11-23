package com.neirrek.harvester.exception;

public class HarvesterException extends RuntimeException {

    private static final long serialVersionUID = 5443873764986661772L;

    public HarvesterException(String message, Throwable cause) {
        super(message, cause);
    }

}
