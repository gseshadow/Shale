package com.shale.server.controller;

import com.shale.data.validation.ContactValues;
import com.shale.server.runtime.ServerRuntimeSessionState;
import org.springframework.web.bind.annotation.*;

/** Advisory form feedback uses exactly the same implementation as authoritative persistence. */
@RestController
public final class ContactValueValidationController {
    private final ServerRuntimeSessionState session;
    public ContactValueValidationController(ServerRuntimeSessionState session){this.session=session;}
    public record Request(String kind,String value,String extension) {}
    @PostMapping("/api/validation/contact-value")
    public Object validate(@RequestBody Request request){
        session.requireShaleClientId();session.requireUserId();
        if(request==null)throw new IllegalArgumentException("A validation request is required.");
        if("phone".equals(request.kind())){var v=ContactValues.INSTANCE.phone(request.value(),request.extension(),false,"phone");return v==null?java.util.Map.of("valid",true):v;}
        if("email".equals(request.kind())){var v=ContactValues.INSTANCE.email(request.value(),false,"email");return v==null?java.util.Map.of("valid",true):v;}
        throw new IllegalArgumentException("Unsupported contact field.");
    }
}
