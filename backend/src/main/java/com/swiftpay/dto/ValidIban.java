package com.swiftpay.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.math.BigInteger;
import java.util.regex.Pattern;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidIban.Validator.class)
public @interface ValidIban {
    String message() default "destinationIban is not a well-formed, valid-checksum IBAN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIban, String> {

        // Strict format: exactly two uppercase ASCII letters (country code) followed by
        // digits only. Rejects lowercase, spaces, dashes and any non-ASCII look-alike
        // characters (e.g. Cyrillic letters, full-width digits) outright.
        private static final Pattern FORMAT = Pattern.compile("^[A-Z]{2}[0-9]{2,32}$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return false;
            }
            // This system only issues TR IBANs (26 chars total); reject anything else
            // outright rather than accepting a syntactically valid IBAN we could never own.
            if (value.length() != 26 || !value.startsWith("TR")) {
                return false;
            }
            if (!FORMAT.matcher(value).matches()) {
                return false;
            }
            return mod97Checksum(value) == 1;
        }

        private long mod97Checksum(String iban) {
            String rearranged = iban.substring(4) + iban.substring(0, 4);
            StringBuilder numeric = new StringBuilder();
            for (char c : rearranged.toCharArray()) {
                if (Character.isDigit(c)) {
                    numeric.append(c);
                } else {
                    numeric.append(Character.getNumericValue(c));
                }
            }
            return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).longValue();
        }
    }
}
