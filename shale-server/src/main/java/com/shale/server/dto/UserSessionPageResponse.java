package com.shale.server.dto;
import java.util.List;
public record UserSessionPageResponse(List<UserSessionResponse> items,int page,int size) {}
