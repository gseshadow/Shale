package com.shale.server.dto;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path, java.util.List<com.shale.core.validation.FieldValidationException.FieldError> fieldErrors) {
    public ApiErrorResponse(Instant timestamp,int status,String error,String message,String path){this(timestamp,status,error,message,path,java.util.List.of());}
}
