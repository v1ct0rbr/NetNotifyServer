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
}
