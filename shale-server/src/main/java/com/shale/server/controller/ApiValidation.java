package com.shale.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shale.server.dto.LoginRequest;

final class ApiValidation {
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;
    private static final int MAX_PAGE = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_PASSWORD_LENGTH = 1024;
    private static final int MAX_NOTE_TEXT_LENGTH = 10_000;
    private static final int MAX_TASK_TITLE_LENGTH = 255;
    private static final int MAX_TASK_DESCRIPTION_LENGTH = 10_000;

    private ApiValidation() {
    }

    static LoginRequest requireValidLogin(LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            throw badRequest("Email and password are required.");
        }
        String email = request.email().trim();
        if (email.length() > MAX_EMAIL_LENGTH || !email.contains("@")) {
            throw badRequest("A valid email is required.");
        }
        if (request.password().length() > MAX_PASSWORD_LENGTH) {
            throw badRequest("Password is too long.");
        }
        return new LoginRequest(email, request.password());
    }

    static String searchQuery(String query) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw badRequest("Search query must be 100 characters or fewer.");
        }
        return safeQuery;
    }

    static String noteText(String noteText) {
        String safeNoteText = noteText == null ? "" : noteText.trim();
        if (safeNoteText.isEmpty()) {
            throw badRequest("Note text is required.");
        }
        if (safeNoteText.length() > MAX_NOTE_TEXT_LENGTH) {
            throw badRequest("Note text must be 10000 characters or fewer.");
        }
        return safeNoteText;
    }

    static String taskTitle(String title) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            throw badRequest("Task title is required.");
        }
        if (safeTitle.length() > MAX_TASK_TITLE_LENGTH) {
            throw badRequest("Task title must be 255 characters or fewer.");
        }
        return safeTitle;
    }

    static String optionalTaskDescription(String description) {
        String safeDescription = description == null ? "" : description.trim();
        if (safeDescription.isEmpty()) {
            return null;
        }
        if (safeDescription.length() > MAX_TASK_DESCRIPTION_LENGTH) {
            throw badRequest("Task description must be 10000 characters or fewer.");
        }
        return safeDescription;
    }

    static String optionalDateText(String dateText, String fieldName) {
        String safeDateText = dateText == null ? "" : dateText.trim();
        if (safeDateText.isEmpty()) {
            return null;
        }
        if (!safeDateText.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw badRequest(fieldName + " must use YYYY-MM-DD format.");
        }
        return safeDateText;
    }

    static long positiveId(long id, String fieldName) {
        if (id <= 0) {
            throw badRequest(fieldName + " must be positive.");
        }
        return id;
    }

    static int page(int page) {
        if (page < 0 || page > MAX_PAGE) {
            throw badRequest("page must be between 0 and 100.");
        }
        return page;
    }

    static int size(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("size must be between 1 and 100.");
        }
        return size;
    }

    static int searchLimitForPage(int page, int size) {
        return Math.multiplyExact(page + 1, size);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
