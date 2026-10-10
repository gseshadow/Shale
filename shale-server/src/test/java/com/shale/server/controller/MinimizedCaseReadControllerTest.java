package com.shale.server.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shale.core.dto.*;
import com.shale.core.service.*;
import com.shale.server.runtime.*;
import jakarta.servlet.http.HttpServletRequest;

/** Synthetic HTTP/serialization outcomes; no deployed host or live session/SQL acceptance. */
class MinimizedCaseReadControllerTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final List<List<Object>> calls=new ArrayList<>();
    private Object result=new MinimizedCasePage(List.of(item("Name")),0,25,false);
    private RuntimeException failure;
    private static MinimizedCaseOverview item(String name){return new MinimizedCaseOverview(5,null,name,null,null,null,null,null);}
    private MockMvc mvc(boolean authenticated) {
        CaseServicePort port=(CaseServicePort)Proxy.newProxyInstance(CaseServicePort.class.getClassLoader(),new Class<?>[]{CaseServicePort.class},(p,m,a)->{
            calls.add(List.of(m.getName(),Arrays.asList(a)));if(failure!=null)throw failure;return result;
        });
        ObjectProvider<HttpServletRequest> requests=new ObjectProvider<>() {
            public HttpServletRequest getObject(Object...args){return null;}public HttpServletRequest getIfAvailable(){return null;}
            public HttpServletRequest getIfUnique(){return null;}public HttpServletRequest getObject(){return null;}
        };
        var session=new ServerRuntimeSessionState(r->authenticated?ServerSessionContext.authenticated(new ServerPrincipal(31,41,null)):ServerSessionContext.unauthenticated(),requests);
        return MockMvcBuilders.standaloneSetup(new MinimizedCaseReadController(port,session,json))
            .setControllerAdvice(new MinimizedCaseReadErrors(),new ApiExceptionHandler()).build();
    }
    @Test void bodySearchDerivesAuthorityAndDeliversOnlyMinimizedWire() throws Exception {
        var response=mvc(true).perform(post("/api/v2/cases/search-page").contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\" Élan_%[ \"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.items[0].caseName").value("Name")).andReturn().getResponse();
        assertEquals(List.of("searchMinimizedCases",List.of("Élan_%[",41,31,0,25)),calls.getFirst());
        var root=json.readTree(response.getContentAsByteArray());
        assertEquals(Set.of("items","page","size","hasMore"),keys(root));
        assertEquals(Set.of("caseId","caseNumber","caseName","status","practiceArea","responsibleAttorney","primaryLegalAssistant","updatedAt"),keys(root.path("items").get(0)));
        assertTrue(root.path("items").get(0).path("status").isNull());
    }
    @Test void assignedAndOverviewUseSingleVerifiedPrincipalAndSafeUnavailable404() throws Exception {
        mvc(true).perform(get("/api/v2/cases/assigned-page").param("page","100").param("size","25")).andExpect(status().isOk());
        assertEquals(List.of("listMinimizedAssignedCases",List.of(41,31,100,25)),calls.getFirst());
        result=Optional.of(item("Overview"));
        mvc(true).perform(get("/api/v2/cases/5/overview")).andExpect(status().isOk()).andExpect(jsonPath("$.caseName").value("Overview"));
        assertEquals(List.of("readMinimizedCaseOverview",List.of(5L,41,31)),calls.getLast());
        result=Optional.empty();
        mvc(true).perform(get("/api/v2/cases/999/overview")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Case unavailable."));
    }
    @Test void invalidInputsNeverReachPortAndSessionRejectionIsDistinct() throws Exception {
        for(String body:List.of("{\"page\":101}","{\"size\":26}","{\"size\":0}","{\"query\":\""+"x".repeat(101)+"\"}","{\"query\":{\"private\":\"secret\"}}"))
            mvc(true).perform(post("/api/v2/cases/search-page").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc(true).perform(get("/api/v2/cases/2147483648/overview")).andExpect(status().isBadRequest());
        mvc(false).perform(get("/api/v2/cases/5/overview")).andExpect(status().isUnauthorized());
        assertTrue(calls.isEmpty());
        failure=new CaseReadException(CaseReadException.Kind.DENIED);
        mvc(true).perform(get("/api/v2/cases/5/overview")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("case_read_denied"));
    }
    @Test void auditFailureTimeoutAndOversizedResponsesDeliverNoPartialCaseData() throws Exception {
        for(var kind:List.of(CaseReadException.Kind.AUDIT_UNAVAILABLE,CaseReadException.Kind.TIMEOUT)) {
            failure=new CaseReadException(kind,new RuntimeException("Private name and search text"));
            var response=mvc(true).perform(get("/api/v2/cases/5/overview")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("Private"));assertFalse(response.contains("caseName"));
        }
        failure=null;result=Optional.of(escapedItem());
        mvc(true).perform(get("/api/v2/cases/5/overview")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("case_payload_oversized"));
        result=new MinimizedCasePage(Collections.nCopies(25,escapedItem()),0,25,true);
        mvc(true).perform(post("/api/v2/cases/search-page").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"x\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("case_payload_oversized")).andExpect(jsonPath("$.items").doesNotExist());
    }
    @Test void springBodyConversionTracingCannotExposeSearchText() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(
            org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.class);
        var previous=logger.getLevel();
        var events=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();logger.addAppender(events);logger.setLevel(ch.qos.logback.classic.Level.TRACE);
        try {
            mvc(true).perform(post("/api/v2/cases/search-page").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"PrivateSearchWitness\"}")).andExpect(status().isOk());
            assertTrue(events.list.stream().anyMatch(e->e.getFormattedMessage().contains("query=REDACTED")),
                "Exercise Spring's actual body formatting, not just the request's toString method");
            assertTrue(events.list.stream().noneMatch(e->e.getFormattedMessage().contains("PrivateSearchWitness")),
                "Framework body-conversion tracing must not expose the query");
        } finally {logger.setLevel(previous);logger.detachAppender(events);events.stop();}
    }
    @Test void scopedErrorPrivacyNeverIncludesQueryBodyOrExceptionMetadata() {
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/v2/cases/search-page");
        request.setContent("{\"query\":\"Private search\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=new MinimizedCaseReadErrors().failure(new IllegalArgumentException("Private search"),request);
        assertEquals(400,response.getStatusCode().value());assertFalse(response.getBody().toString().contains("Private search"));
    }
    private static MinimizedCaseOverview escapedItem() {
        String value="\u0001".repeat(255);
        return new MinimizedCaseOverview(5,"\u0001".repeat(200),value,
            new MinimizedCaseOverview.CaseReadStatus(1,value,"#abcdef"),
            new MinimizedCaseOverview.CaseReadPracticeArea(1,value),
            new MinimizedCaseOverview.CaseReadUser(1,value),new MinimizedCaseOverview.CaseReadUser(2,value),null);
    }
    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode node){Set<String> keys=new HashSet<>();node.fieldNames().forEachRemaining(keys::add);return keys;}
}
