package co.edu.unicesar.marketradar.config;

import co.edu.unicesar.marketradar.seguridad.RespuestasSeguridad;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Seguridad de la aplicación: sesión en el servidor, contraseñas con BCrypt, protección CSRF con
 * cookie para el frontend y permisos por rol. El usuario y el administrador ven interfaces distintas
 * y cada una solo puede llamar a su parte de la API.
 */
@Configuration
public class ConfiguracionSeguridad {

    /** Rutas que no exigen sesión: páginas, recursos estáticos, ingreso, registro y salud. */
    private static final String[] RUTAS_PUBLICAS = {
            "/", "/index.html", "/usuario.html", "/admin.html", "/favicon.ico", "/css/**", "/js/**", "/img/**",
            "/api/v1/auth/**", "/api/v1/marketplaces", "/api/v1/salud", "/actuator/health",
            "/api/docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/error"
    };

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, RespuestasSeguridad respuestas) throws Exception {
        CsrfTokenRequestAttributeHandler manejadorCsrf = new CsrfTokenRequestAttributeHandler();
        // Carga el token en cada petición para que la cookie XSRF-TOKEN exista desde la primera página.
        manejadorCsrf.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(manejadorCsrf))
                .authorizeHttpRequests(permisos -> permisos
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/v1/usuario/**", "/api/v1/busqueda/**", "/api/v1/ofertas/**",
                                "/api/v1/asistente/**").hasRole("USUARIO")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .securityContext(contexto -> contexto.securityContextRepository(repositorioContexto()))
                .sessionManagement(sesion -> sesion.sessionFixation(fijacion -> fijacion.changeSessionId()))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas.sinSesion())
                        .accessDeniedHandler(respuestas.sinPermiso()))
                .logout(salida -> salida
                        .logoutUrl("/api/v1/auth/salida")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .formLogin(formulario -> formulario.disable())
                .httpBasic(basica -> basica.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorClaves() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository repositorioContexto() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager gestorAutenticacion(UserDetailsService detalles, PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(codificador);
        proveedor.setUserDetailsService(detalles);
        return new ProviderManager(proveedor);
    }
}
