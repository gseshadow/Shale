package com.shale.data.security.ai;

import com.shale.core.dto.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Closed internal structured result, with no transport envelope. Writes only to the bounded private sink. */
final class MinimizedCaseEncoder implements IntegrationCaseReadService.Encoder {
    @Override public void encode(Object result, UUID correlation, OutputStream out)throws IOException {
        if(result instanceof MinimizedCasePage page) {
            if(page.size()!=10 || page.page()>9) throw new IOException("Invalid minimized page.");
            raw(out,"{\"items\":["); boolean first=true;
            for(var item:page.items()) { if(!first)raw(out,","); first=false; overview(out,item); }
            raw(out,"],\"page\":"+page.page()+",\"size\":10,\"hasMore\":"+page.hasMore()+",\"windowExhausted\":"+(page.page()==9&&page.hasMore())+",\"correlationId\":");
        } else if(result instanceof MinimizedCaseOverview item) {
            raw(out,"{\"case\":"); overview(out,item); raw(out,",\"correlationId\":");
        } else throw new IOException("Minimized projection required.");
        text(out,correlation.toString()); raw(out,"}");
    }
    private static void overview(OutputStream out,MinimizedCaseOverview v)throws IOException {
        if(v.updatedAt()!=null) {
            if(v.updatedAt().length()>29 || !v.updatedAt().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]{1,9})?)?")) throw new IOException("Invalid stored local time.");
            try { LocalDateTime.parse(v.updatedAt()); } catch(RuntimeException invalid) { throw new IOException("Invalid stored local time."); }
        }
        raw(out,"{\"caseId\":"+v.caseId()+",\"caseNumber\":"); text(out,v.caseNumber());
        raw(out,",\"caseName\":"); text(out,v.caseName()); raw(out,",\"status\":");
        if(v.status()==null)raw(out,"null"); else {
            var s=v.status(); if(s.color()!=null&&!s.color().matches("#[0-9a-fA-F]{6}"))throw new IOException("Invalid status color.");
            raw(out,"{\"id\":"+s.id()+",\"name\":"); text(out,s.name()); raw(out,",\"color\":"); text(out,s.color()); raw(out,"}");
        }
        raw(out,",\"practiceArea\":");
        if(v.practiceArea()==null)raw(out,"null"); else { raw(out,"{\"id\":"+v.practiceArea().id()+",\"name\":");text(out,v.practiceArea().name());raw(out,"}"); }
        raw(out,",\"responsibleAttorney\":"); user(out,v.responsibleAttorney());
        raw(out,",\"primaryLegalAssistant\":"); user(out,v.primaryLegalAssistant());
        raw(out,",\"updatedAt\":"); text(out,v.updatedAt()); raw(out,"}");
    }
    private static void user(OutputStream out,MinimizedCaseOverview.CaseReadUser user)throws IOException {
        if(user==null)raw(out,"null"); else { raw(out,"{\"userId\":"+user.userId()+",\"displayName\":");text(out,user.displayName());raw(out,"}"); }
    }
    private static void text(OutputStream out,String value)throws IOException {
        if(value==null) { raw(out,"null");return; }
        raw(out,"\"");
        var escaped=new StringBuilder();
        for(int n=0;n<value.length();n++) {
            char ch=value.charAt(n);
            if(ch=='"'||ch=='\\')escaped.append('\\').append(ch);
            else if(ch<32)escaped.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)ch));
            else escaped.append(ch);
        }
        var encoding=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer encoded=encoding.encode(CharBuffer.wrap(escaped));
        byte[] bytes=new byte[encoded.remaining()]; encoded.get(bytes); out.write(bytes); raw(out,"\"");
    }
    private static void raw(OutputStream out,String fixed)throws IOException { out.write(fixed.getBytes(StandardCharsets.UTF_8)); }
}
