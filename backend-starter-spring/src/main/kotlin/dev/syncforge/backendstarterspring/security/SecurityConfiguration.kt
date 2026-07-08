package dev.syncforge.backendstarterspring.security

import dev.syncforge.server.auth.InMemoryAuthStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class SecurityConfiguration(
    private val bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/**", "/health").permitAll()
                    .requestMatchers("/sync/**").authenticated()
                    .anyRequest().permitAll()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

@Component
class BearerTokenAuthenticationFilter(
    private val authStore: InMemoryAuthStore,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.requestURI.startsWith("/sync/")) {
            val header = request.getHeader("Authorization")
            val token = header
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.removePrefix("Bearer ")
                ?.trim()
            val email = authStore.validateAccessToken(token)
            if (email == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            val authentication = UsernamePasswordAuthenticationToken(email, null, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
            request.setAttribute(SYNCFORGE_USER_EMAIL, email)
        }
        filterChain.doFilter(request, response)
    }
}

const val SYNCFORGE_USER_EMAIL: String = "syncforge.user.email"