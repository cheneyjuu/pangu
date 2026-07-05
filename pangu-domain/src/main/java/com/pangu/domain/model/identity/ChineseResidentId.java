package com.pangu.domain.model.identity;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;

public final class ChineseResidentId {

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private ChineseResidentId() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    public static boolean isValid(String raw) {
        String value = normalize(raw);
        if (value == null || !value.matches("^\\d{17}[\\dX]$")) {
            return false;
        }
        if (!hasValidBirthDate(value)) {
            return false;
        }
        return checksum(value.substring(0, 17)) == value.charAt(17);
    }

    public static String mask(String raw) {
        String value = normalize(raw);
        if (value == null || value.length() < 10) {
            return "********";
        }
        return value.substring(0, 3) + "***********" + value.substring(value.length() - 4);
    }

    public static boolean isPlaceholder(String realName, String idCardNumber) {
        return startsWithMock(realName) || startsWithMock(idCardNumber);
    }

    private static boolean startsWithMock(String value) {
        return value != null && value.trim().toUpperCase(Locale.ROOT).startsWith("MOCK_");
    }

    private static boolean hasValidBirthDate(String value) {
        try {
            int year = Integer.parseInt(value.substring(6, 10));
            int month = Integer.parseInt(value.substring(10, 12));
            int day = Integer.parseInt(value.substring(12, 14));
            LocalDate birthDate = LocalDate.of(year, month, day);
            return !birthDate.isAfter(LocalDate.now()) && year >= 1900;
        } catch (DateTimeException | NumberFormatException e) {
            return false;
        }
    }

    private static char checksum(String first17Digits) {
        int sum = 0;
        for (int i = 0; i < first17Digits.length(); i++) {
            sum += Character.digit(first17Digits.charAt(i), 10) * WEIGHTS[i];
        }
        return CHECK_CODES[sum % 11];
    }
}
