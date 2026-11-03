package com.pva.app.util;

import com.pva.app.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Utilidades de seguridad transversales para la capa de presentación y servicios.
 * Centraliza la extracción y validación del principal de autenticación (ID de operario).
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Clase de utilidad no instanciable
    }

    /**
     * Extrae el ID del operario autenticado desde el objeto Authentication de Spring Security.
     *
     * @param authentication contexto de autenticación actual
     * @return ID numérico del operario
     * @throws AppException si el token no existe, está expirado o tiene formato inválido
     */
    public static Long extraerIdOperario(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
            }
        }
        throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
    }
}
