package com.fiscalhub.backend.util;

public final class CnpjValidationUtils {

    private CnpjValidationUtils() {
    }

    public static String normalize(String cnpj) {
        if (cnpj == null) {
            return "";
        }
        return cnpj.replaceAll("\\D", "");
    }

    public static boolean isValid(String cnpj) {
        String digits = normalize(cnpj);

        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int primeiro = calcularDigito(digits.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        if (primeiro != Character.getNumericValue(digits.charAt(12))) {
            return false;
        }

        int segundo = calcularDigito(digits.substring(0, 13), new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return segundo == Character.getNumericValue(digits.charAt(13));
    }

    public static boolean isValidCpf(String cpf) {
        String digits = normalize(cpf);
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }
        int primeiro = calcularDigito(digits.substring(0, 9), new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2});
        if (primeiro != Character.getNumericValue(digits.charAt(9))) {
            return false;
        }
        int segundo = calcularDigito(digits.substring(0, 10), new int[]{11, 10, 9, 8, 7, 6, 5, 4, 3, 2});
        return segundo == Character.getNumericValue(digits.charAt(10));
    }

    private static int calcularDigito(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += Character.getNumericValue(base.charAt(i)) * pesos[i];
        }

        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
