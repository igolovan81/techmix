package com.testingai.graphql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Same demo users/roles as {@code backend/rest-api}'s SecurityConfig, with one deliberate difference: passwords carry
 * an explicit {@code {noop}} encoding prefix. Spring Security 6.x's default DelegatingPasswordEncoder throws
 * IllegalArgumentException matching a password with no {@code {id}} prefix (verified against backend/rest-api's exact
 * unprefixed pattern), so this is required for Basic auth to actually work here, not a stylistic choice.
 *
 * <p>
 * {@code /graphql} and {@code /graphiql} are {@code permitAll()} at the HTTP layer — GraphQL has a single endpoint for
 * every operation, so per-operation authorization is enforced with {@code @PreAuthorize} on
 * {@link com.testingai.graphql.controller.DemoController}'s individual methods instead.
 *
 * <p>
 * {@code httpBasic()}'s {@link org.springframework.security.web.authentication.www.BasicAuthenticationFilter} defaults
 * to its own {@code RequestAttributeSecurityContextRepository} (verified by decompiling spring-security-web 6.4.4),
 * which never touches the {@code HttpSession} — so without the explicit {@link HttpSessionSecurityContextRepository}
 * below, a Basic-authenticated request never gets a {@code JSESSIONID}. That session is required for the
 * {@code reviewAdded} subscription: a browser's WebSocket API cannot attach an {@code Authorization} header to the
 * handshake, so the angular-demo client authenticates once over HTTP and relies on the resulting session cookie being
 * sent automatically on the same-origin WebSocket handshake instead.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public UserDetailsService userDetailsService() {
		UserDetails user = User.withUsername("user").password("{noop}userPassword").roles("USER").build();
		UserDetails admin = User.withUsername("admin").password("{noop}adminPassword").roles("ADMIN").build();
		return new InMemoryUserDetailsManager(user, admin);
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.requestMatchers("/graphql", "/graphiql").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/products/*/image").permitAll().anyRequest()
						.authenticated())
				.httpBasic(basic -> basic.securityContextRepository(new HttpSessionSecurityContextRepository()));
		return http.build();
	}
}
