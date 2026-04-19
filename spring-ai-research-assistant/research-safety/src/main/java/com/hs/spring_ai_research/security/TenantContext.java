package com.hs.spring_ai_research.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadLocal-based tenant isolation for multi-tenant AI systems.
 *
 * In a multi-tenant RAG system, Tenant A must NEVER see Tenant B's documents.
 * This is achieved by:
 * 1. Setting the tenant ID at request entry (from auth token or header)
 * 2. Automatically scoping all vector DB queries to the current tenant
 * 3. Adding tenant metadata to all ingested documents
 *
 * Without tenant isolation, a single shared vector store becomes a data leak.
 * Example: Company A's confidential docs appearing in Company B's search results.
 *
 * In production, this integrates with Spring Security to extract tenant from JWT.
 * For this POC, the tenant comes from a request header (X-Tenant-Id).
 */
@Slf4j
@Component
public class TenantContext {

	private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

	@Value("${app.security.default-tenant:default}")
	private String defaultTenant;

	public void setTenant(String tenantId) {
		currentTenant.set(tenantId);
		log.debug("Tenant context set: {}", tenantId);
	}

	public String getTenant() {
		String tenant = currentTenant.get();
		return tenant != null ? tenant : defaultTenant;
	}

	public void clear() {
		currentTenant.remove();
	}

	/**
	 * Check if the current tenant owns a resource.
	 */
	public boolean isOwner(String resourceTenantId) {
		String current = getTenant();
		return current.equals(resourceTenantId) || "admin".equals(current);
	}
}
