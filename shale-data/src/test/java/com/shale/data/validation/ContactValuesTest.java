package com.shale.data.validation;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.validation.FieldValidationException;
import org.junit.jupiter.api.Test;

class ContactValuesTest {
    private final ContactValues v = ContactValues.INSTANCE;
    @Test void blanksRespectRequiredAndExtensions() {
        assertNull(v.phone("  ", null, false, "phone"));
        assertNull(v.email(null, false, "email"));
        assertThrows(FieldValidationException.class, () -> v.phone("", null, true, "phone"));
        assertThrows(FieldValidationException.class, () -> v.phone(null, "1", false, "phone"));
        assertThrows(FieldValidationException.class, () -> v.email("", true, "email"));
    }
    @Test void validNumberingPlansAndExtensionExtraction() {
        for (String n : new String[]{"(303) 555-0123", "303.555.0123", "+1 303 555 0123"})
            assertEquals("+13035550123", v.phone(n, null, true, "phone").canonicalNumber());
        for(String n:new String[]{"+61 412 345 678","+49 30 901820","1-800-234-5678","(202) 456-1111"})assertNotNull(v.phone(n,null,true,"phone"));
        assertEquals("000000000001",v.phone("3035550123","000000000001",true,"phone").extension());
        assertEquals("+442079460018", v.phone("+44 20 7946 0018", null, true, "phone").canonicalNumber());
        for (String suffix : new String[]{" x0012", " ext. 0012", ";ext=0012", " extension 0012"}) {
            var result = v.phone("3035550123"+suffix, "0012", true, "phone");
            assertEquals("0012", result.extension());
            assertEquals("3035550123", result.displayInput());
        }
        assertThrows(FieldValidationException.class, () -> v.phone("3035550123 x1", "2", true, "phone"));
        assertThrows(FieldValidationException.class, () -> v.phone("3035550123", "１２", true, "phone"));
    }
    @Test void unusableValuesCannotBeParsedAsPhones() {
        for (String n : new String[]{"0", "0000000000", "911", "123", "call 3035550123", "3035550123,7205550123", "3035550123 / 7205550123", "3035550123 x1234567890123", "2021234567"})
            assertThrows(FieldValidationException.class, () -> v.phone(n, null, true, "phone"), n);
    }
    @Test void supportedEmailsPreserveDisplayAndExistingComparisonSemantics() {
        for (String e : new String[]{" O'Brien+tag@Sub.Example.technology ", "me@bücher.de", "me@example.international", "me@straße.de"}) {
            var result = v.email(e, true, "email");
            assertEquals(e.strip(), result.displayInput());
            assertEquals(e.strip().toLowerCase(java.util.Locale.ROOT), result.comparisonKey());
        }
        assertEquals("me@xn--strae-oqa.de",v.email("me@straße.de",true,"email").transportAddress());
        assertEquals("me@xn--bcher-kva.de", v.email("me@bücher.de", true, "email").transportAddress());
    }
    @Test void malformedAndUnsupportedAreDistinctAndSafe() {
        for (String e : new String[]{"a@b", "a..b@example.com", "a@-example.com", "a@b..com", "A <a@example.com>", "a@example.com,b@example.com", "a@example.com\r\nBcc:b@example.com"})
            assertEquals("invalid_email", assertThrows(FieldValidationException.class, () -> v.email(e, true, "email")).errors().getFirst().code());
        for (String e : new String[]{"\"a b\"@example.com", "me@[127.0.0.1]", "é@example.com", "\"a@b,c\"@example.com"})
            assertEquals("unsupported_format", assertThrows(FieldValidationException.class, () -> v.email(e, true, "email")).errors().getFirst().code());
    }
}
