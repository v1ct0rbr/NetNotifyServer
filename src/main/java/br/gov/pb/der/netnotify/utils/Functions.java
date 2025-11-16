package br.gov.pb.der.netnotify.utils;

import org.springframework.validation.BindingResult;

public class Functions {

    public static String errorStringfy(BindingResult bindingResult) {
        StringBuilder sb = new StringBuilder();
        bindingResult.getAllErrors().forEach(error -> {
            sb.append(error.getDefaultMessage()).append("; ");
        });
        return sb.toString();
    }

    public static String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    public static String removeHtmlTags(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<[^>]*>", "");
    }

    public static String sanitizeAndTruncate(String input, int maxLength) {
        String noHtml = removeHtmlTags(input);
        return truncateString(noHtml, maxLength);
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String capitalizeFirstLetter(String str) {
        if (isNullOrEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String safeTrim(String str) {
        return str == null ? null : str.trim();
    }

    public static String toLowerCaseForQuery(String str) {
        return str == null ? null : "%" + str.toLowerCase() + "%";
    }

    public static Boolean isNumberValid(Number number) {
        return number != null && number.doubleValue() >= 0;
    }

}
