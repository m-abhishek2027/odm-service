package com.odm.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects request bodies larger than odm.api.max-request-bytes with 413 before they are parsed. A declared
 * Content-Length is checked up front; a body without one (chunked) is cut off while it is read, which surfaces as
 * {@link PayloadTooLargeException} and is answered with 413 by the exception handler.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

	private final long maxBytes;

	public RequestSizeLimitFilter(@Value("${odm.api.max-request-bytes:2097152}") long maxBytes) {
		this.maxBytes = maxBytes;
	}

	public static class PayloadTooLargeException extends IOException {

		private static final long serialVersionUID = 1L;

		public PayloadTooLargeException(long maxBytes) {
			super("Request body exceeds " + maxBytes + " bytes");
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String method = request.getMethod();
		return !("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > maxBytes) {
			response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
			response.setContentType("application/problem+json");
			response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Payload Too Large\",\"status\":413,"
					+ "\"detail\":\"Request body exceeds " + maxBytes + " bytes\"}");
			return;
		}
		chain.doFilter(request.getContentLengthLong() < 0 ? new LimitedRequest(request, maxBytes) : request, response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long maxBytes;

		private ServletInputStream stream;

		LimitedRequest(HttpServletRequest request, long maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				stream = new LimitedInputStream(super.getInputStream(), maxBytes);
			}
			return stream;
		}
	}

	private static final class LimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;

		private final long maxBytes;

		private long read;

		LimitedInputStream(ServletInputStream delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int b = delegate.read();
			if (b >= 0) {
				count(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int n = delegate.read(buffer, offset, length);
			if (n > 0) {
				count(n);
			}
			return n;
		}

		private void count(int n) throws PayloadTooLargeException {
			read += n;
			if (read > maxBytes) {
				throw new PayloadTooLargeException(maxBytes);
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}
	}

}
