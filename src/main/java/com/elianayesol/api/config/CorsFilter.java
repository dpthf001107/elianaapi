package com.elianayesol.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		String origin = request.getHeader("Origin");
		
		// 허용된 Origin 확인
		if (origin != null && isAllowedOrigin(origin)) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.setHeader("Access-Control-Allow-Credentials", "true");
			response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
			response.setHeader("Access-Control-Allow-Headers", 
				"Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control");
			response.setHeader("Access-Control-Max-Age", "3600");
		}

		// OPTIONS 요청 (preflight) 처리
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		chain.doFilter(req, res);
	}

	private boolean isAllowedOrigin(String origin) {
		return origin.equals("http://localhost:3000") ||
			   origin.equals("http://127.0.0.1:3000") ||
			   origin.equals("https://www.elianayesol.com") ||
			   origin.equals("https://elianayesol.com");
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// 초기화 로직 없음
	}

	@Override
	public void destroy() {
		// 정리 로직 없음
	}
}

