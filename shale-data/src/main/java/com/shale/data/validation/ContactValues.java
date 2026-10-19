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
    private static final Pattern EXT = Pattern.compile("(?i)(?:\\bextension\\b|\\bext\\.?|(?<![A-Za-z])x|;ext=)\\s*:?\\s*(.*)$");
    private static final Pattern INTERNATIONAL_MAIN = Pattern.compile("\\+[0-9() .\\-/]+");
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private ContactValues() {}

    @Override public Phone phone(String input, String extension, boolean required, String field) {
        String value = trim(input), separate = trim(extension);
        if (value == null) {
            if (separate != null) throw error(field + ".extension", "phone_required", "Enter a phone number before adding an extension.");
            if (required) throw error(field, "required", "Enter a usable phone number.");
            return null;
        }
        String main = value, inline = null;
        var match = EXT.matcher(main);
        if (match.find()) {
            inline = match.group(1).strip();
            main = main.substring(0, match.start()).strip();
            if (main.isBlank()) throw error(field + ".extension", "phone_required", "Enter a phone number before adding an extension.");
        }
        if (separate != null && !separate.matches("[0-9]{1,12}")) throw invalidExtension(field);
        if (inline != null && !inline.matches("[0-9]{1,12}")) throw invalidExtension(field);
        if (inline != null && separate != null && !inline.equals(separate))
            throw error(field + ".extension", "conflicting_extension", "The inline and separate extensions must match.");
        String ext = separate == null ? inline : separate;
        // Explicit international input keeps numbering-plan validation and does not use US lengths.
        if (main.startsWith("+")) {
            if (!INTERNATIONAL_MAIN.matcher(main).matches()) throw invalidPhone(field);
            return fullPhone(main, ext, field, true, false);
        }
        // Extract only ASCII digits, after removing the extension. Never map letters to keypad digits.
        String digits = main.replaceAll("[^0-9]", "");
        if (digits.length() == 7) {
            if (!validExchange(digits)) throw invalidPhone(field);
            String display = digits.substring(0, 3) + "-" + digits.substring(3);
            return new Phone(display, null, ext, withExtension(display, ext)
                    + " · US local; area code required to call", PhoneKind.US_LOCAL, digits);
        }
        boolean countryPrefix = digits.length() == 11;
        if (digits.length() != 10 && !countryPrefix || countryPrefix && digits.charAt(0) != '1') throw invalidPhone(field);
        String national = countryPrefix ? digits.substring(1) : digits;
        if (!validExchange(national) || !validExchange(national.substring(3))) throw invalidPhone(field);
        return fullPhone(digits, ext, field, false, countryPrefix);
    }

    private static boolean validExchange(String digits) {
        return digits.charAt(0) >= '2' && digits.charAt(0) <= '9' && !digits.substring(1, 3).equals("11");
    }

    private static Phone fullPhone(String main, String ext, String field, boolean international, boolean countryPrefix) {
        try {
            var parsed = PHONES.parse(main, DEFAULT_PHONE_REGION);
            if (!PHONES.isValidNumber(parsed)) throw invalidPhone(field);
            String canonical = PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            String display = PHONES.format(parsed, international
                    ? PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL : PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            if (parsed.getCountryCode() == 1 && (international || countryPrefix))
                display = "+1 " + PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            return new Phone(display, canonical, ext, withExtension(display, ext),
                    parsed.getCountryCode() == 1 ? PhoneKind.US_FULL : PhoneKind.INTERNATIONAL, null);
        } catch (NumberParseException e) { throw invalidPhone(field); }
    }

    private static String withExtension(String display, String extension) {
        return display + (extension == null ? "" : " ext. " + extension);
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
    public boolean dialablePhone(String input, String ext) {
        try { return phone(input, ext, true, "phone").dialableWithoutContext(); }
        catch (FieldValidationException e) { return false; }
    }
    public boolean usableEmail(String input) { try { return email(input, true, "email") != null; } catch (FieldValidationException e) { return false; } }
    public static String trim(String input) { return input == null || input.strip().isEmpty() ? null : input.strip(); }
    private static boolean controls(String v) { return v.chars().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029); }
    private static FieldValidationException invalidPhone(String field) { return error(field, "invalid_phone", "Enter a valid US full or 7-digit local number. Use +country code for international numbers."); }
    private static FieldValidationException invalidExtension(String field) { return error(field + ".extension", "invalid_extension", "Enter an extension of 1–12 digits (0–9)."); }
    private static FieldValidationException invalidEmail(String field) { return error(field, "invalid_email", "Enter one email address, such as name@example.com."); }
    private static FieldValidationException error(String field, String code, String message) { return new FieldValidationException(field, code, message); }
}
