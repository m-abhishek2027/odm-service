package com.odm.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Kept out of OdmServiceApplication so @WebMvcTest slices don't need JPA
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

	public static final String USER_HEADER = "X-User-Id";

	private static final String DEFAULT_AUDITOR = "system";

	// createdBy/modifiedBy come from the X-User-Id header until real authentication is added
	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> {
			if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
				String user = attrs.getRequest().getHeader(USER_HEADER);
				if (StringUtils.hasText(user)) {
					return Optional.of(user.trim());
				}
			}
			return Optional.of(DEFAULT_AUDITOR);
		};
	}

}
