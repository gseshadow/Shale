package com.shale.data.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.shale.core.validation.ContactValueValidator;
import com.shale.core.validation.FieldValidationException;
import com.ibm.icu.text.IDNA;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared desktop/server implementation; has no UI, network or database dependency. */
public final class ContactValues implements ContactValueValidator {
    public static final ContactValues INSTANCE = new ContactValues();
    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    private static final IDNA DOMAINS=IDNA.getUTS46Instance(IDNA.USE_STD3_RULES|IDNA.CHECK_BIDI|IDNA.CHECK_CONTEXTJ|IDNA.NONTRANSITIONAL_TO_ASCII);
    private static final Pattern EXT = Pattern.compile("(?i)(?:\\s*(?:ext\\.?|extension|x|;ext=)\\s*:?\\s*([0-9]+))$");
    private static final Pattern MAIN = Pattern.compile("\\+?[0-9() .\\-/]+");
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private ContactValues() {}

    @Override public Phone phone(String input, String extension, boolean required, String field) {
        String value = trim(input), separate = trim(extension);
        if (value == null) {
            if (separate != null) throw error(field + ".extension", "phone_required", "Enter a phone number before adding an extension.");
            if (required) throw error(field, "required", "Enter a usable phone number.");
            return null;
        }
        if (value.length() > 255 || controls(value)) throw invalidPhone(field);
        String main = value, inline = null;
        var match = EXT.matcher(main);
        if (match.find()) { inline = match.group(1); main = main.substring(0, match.start()).trim();if(main.isBlank())throw error(field+".extension","phone_required","Enter a phone number before adding an extension."); }
        if (separate != null && !separate.matches("[0-9]{1,12}")) throw invalidExtension(field);
        if (inline != null && !inline.matches("[0-9]{1,12}")) throw invalidExtension(field);
        if (inline != null && separate != null && !inline.equals(separate))
            throw error(field + ".extension", "conflicting_extension", "The inline and separate extensions must match.");
        String ext = separate == null ? inline : separate;
        // Do not let the parser's vanity-number or first-number extraction accept prose/lists.
        if (!MAIN.matcher(main).matches() || main.chars().filter(c -> c == '+').count() > 1) throw invalidPhone(field);
        try {
            var parsed = PHONES.parse(main, DEFAULT_PHONE_REGION);
            if (!PHONES.isValidNumber(parsed)) throw invalidPhone(field);
            String canonical = PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            String preview = PHONES.format(parsed, parsed.getCountryCode() == 1
                    ? PhoneNumberUtil.PhoneNumberFormat.NATIONAL : PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
            return new Phone(main, canonical, ext, preview + (ext == null ? "" : " ext. " + ext));
        } catch (NumberParseException e) { throw invalidPhone(field); }
    }

    @Override public Email email(String input, boolean required, String field) {
        String value = trim(input);
        if (value == null) {
            if (required) throw error(field, "required", "Enter an email address.");
            return null;
        }
        if (controls(value) || value.length() > 320
                || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) throw invalidEmail(field);
        if(value.startsWith("\""))throw error(field,"unsupported_format","Quoted email local parts are not supported. Use an unquoted address.");
        if(value.indexOf(',')>=0||value.indexOf(';')>=0)throw invalidEmail(field);
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length()-1) throw invalidEmail(field);
        String local = value.substring(0, at), domain = value.substring(at+1);
        if (local.startsWith("\"") || domain.startsWith("[") || !local.chars().allMatch(c -> c < 128))
            throw error(field, "unsupported_format", "This email format is not supported. Use an unquoted address with an ASCII local part and a domain name.");
        if (local.length() > 64 || !LOCAL.matcher(local).matches() || local.startsWith(".")
                || local.endsWith(".") || local.contains("..")) throw invalidEmail(field);
        var domainInfo=new IDNA.Info();var domainAscii=new StringBuilder();
        DOMAINS.nameToASCII(domain,domainAscii,domainInfo);
        if(domainInfo.hasErrors())throw invalidEmail(field);
        String ascii=domainAscii.toString();
        if (ascii.length() > 253 || !ascii.contains(".") || ascii.endsWith(".")) throw invalidEmail(field);
        String[] labels = ascii.split("\\.", -1);
        for (String label : labels) if (label.isEmpty() || label.length() > 63
                || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")) throw invalidEmail(field);
        if (labels[labels.length-1].matches("[0-9]+")) throw invalidEmail(field);
        return new Email(value, value.toLowerCase(Locale.ROOT), local + "@" + ascii);
    }
    public boolean usablePhone(String input, String ext) { try { return phone(input, ext, true, "phone") != null; } catch (FieldValidationException e) { return false; } }
    public boolean usableEmail(String input) { try { return email(input, true, "email") != null; } catch (FieldValidationException e) { return false; } }
    public static String trim(String input) { return input == null || input.strip().isEmpty() ? null : input.strip(); }
    private static boolean controls(String v) { return v.chars().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029); }
    private static FieldValidationException invalidPhone(String field) { return error(field, "invalid_phone", "Enter a complete valid phone number. Use +country code for international numbers; US is the default."); }
    private static FieldValidationException invalidExtension(String field) { return error(field + ".extension", "invalid_extension", "Enter an extension of 1–12 digits (0–9)."); }
    private static FieldValidationException invalidEmail(String field) { return error(field, "invalid_email", "Enter one email address, such as name@example.com."); }
    private static FieldValidationException error(String field, String code, String message) { return new FieldValidationException(field, code, message); }
}
