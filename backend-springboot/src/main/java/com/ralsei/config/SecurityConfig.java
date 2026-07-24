package com.ralsei.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ralsei.security.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;

/**
 * Configures Spring Security for the backend application.
 *
 * <p>This class defines the HTTP security rules for the API, registers the JWT-based
 * authentication flow, and ensures requests are handled in a stateless manner.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
/**
 * Configures security for the application.
 */
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    /**
     * Builds the HTTP security filter chain for the application.
     *
     * <p>The configuration permits unauthenticated access to public authentication and
     * booking-related endpoints while requiring authentication for all other requests.
     * The JWT authentication filter is inserted before the username/password filter to
     * support stateless bearer-token validation.
     *
     * @param http the HTTP security configuration to customize
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the security configuration cannot be built
     */
    @Bean
    /**
     * Executes the security filter chain operation.
     *
     * @param http the value supplied for this operation
     *
     * @return the operation result
     *
     * @throws Exception if the operation fails
     */
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configure(http))
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payment/**").permitAll()
                        .requestMatchers("/api/v1/bookings/**").permitAll()
                        .requestMatchers("/api/v1/trips/**").permitAll()
                        .requestMatchers("/api/v1/routes/dropdown").permitAll()
                        .requestMatchers("/api/v1/routes/customer-locations").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll() 
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
