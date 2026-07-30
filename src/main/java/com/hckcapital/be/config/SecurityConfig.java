package com.hckcapital.be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Narrower match evaluated first: unlike the rest of /api/v1/auth/** (which
                // has to stay reachable pre-login — see below), this one needs a real JWT
                // (Settings > Forget Password's own gate, see AuthController.getPasswordStatus).
                // Without this override the broader permitAll below would let an
                // unauthenticated request reach the controller with a null Authentication.
                .requestMatchers("/api/v1/auth/password-status").authenticated()
                // /otp/** has to be reachable pre-signup — there's no JWT yet at that point
                // (see OtpController, used by SignUpScreen's own OTP step before AuthService.
                // signup ever creates an account/session).
                .requestMatchers("/api/v1/auth/**", "/api/v1/otp/**", "/api/v1/health", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
