package com.shale.server.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.shale.data.validation.ContactValues;
import com.shale.core.validation.FieldValidationException;
import com.shale.server.runtime.ServerRuntimeSessionState;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContactValueApiTest {
    private static ServerRuntimeSessionState session(){return new ServerRuntimeSessionState(request->com.shale.server.runtime.ServerSessionContext.authenticated(new com.shale.server.runtime.ServerPrincipal(9,7,null)),new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(jakarta.servlet.http.HttpServletRequest.class));}
    @Test void advisoryApiAndPersistenceParserShareSafeErrorsAndPhoneOutputs()throws Exception{
        var session=session();
        var mvc=MockMvcBuilders.standaloneSetup(new ContactValueValidationController(session)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"phone\",\"value\":\"(303) 555-0123 x001\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canonicalNumber").value("+13035550123")).andExpect(jsonPath("$.extension").value("001"));
        var invalid=assertThrows(FieldValidationException.class,()->ContactValues.INSTANCE.phone("0",null,true,"phone"));
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"phone\",\"value\":\"0\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_failed")).andExpect(jsonPath("$.fieldErrors[0].field").value("phone"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value(invalid.getMessage())).andExpect(jsonPath("$.value").doesNotExist());
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"email\",\"value\":\"\\\"quoted\\\"@example.com\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("unsupported_format"));
    }
    @Test void blankOptionalFieldsGiveAReadableJsonResult()throws Exception{
        var session=session();var mvc=MockMvcBuilders.standaloneSetup(new ContactValueValidationController(session)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"phone\",\"value\":\"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
    }
    @Test void authenticationDoesNotApplyNewAccountEmailGrammar(){
        assertEquals("legacy-account",ApiValidation.requireValidLogin(new com.shale.server.dto.LoginRequest("legacy-account","secret")).email());
    }
    @Test void advisoryLocalNumberExposesLocalStateWithoutAnInventedE164()throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ContactValueValidationController(session())).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"phone\",\"value\":\"555-0123 x001\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("US_LOCAL"))
            .andExpect(jsonPath("$.canonicalNumber").doesNotExist()).andExpect(jsonPath("$.localNumber").value("5550123"))
            .andExpect(jsonPath("$.extension").value("001"));
    }

    @Test void advisoryFormattingMatchesPersistenceForExtractedUsAndInternationalNumbers() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ContactValueValidationController(session())).setControllerAdvice(new ApiExceptionHandler()).build();
        for(String input:new String[]{"Call: (505) 903-3568 x001","(903) 3568 x001","1-505-903-3568 x001","+49 30 901820 x001"}) {
            var parsed=ContactValues.INSTANCE.phone(input,null,true,"phone");
            mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new ContactValueValidationController.Request("phone",input,null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayInput").value(parsed.displayInput()))
                .andExpect(jsonPath("$.normalizedNumber").doesNotExist())
                .andExpect(jsonPath("$.extension").value("001")).andExpect(jsonPath("$.kind").value(parsed.kind().name()));
        }
        mvc.perform(post("/api/validation/contact-value").contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"phone\",\"value\":\"2 505 903 3568\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].code").value("invalid_phone"));
    }

}
