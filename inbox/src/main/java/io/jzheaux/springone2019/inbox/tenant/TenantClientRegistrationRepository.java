package io.jzheaux.springone2019.inbox.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

@Component
@Primary
@Slf4j
public class TenantClientRegistrationRepository implements ReactiveClientRegistrationRepository {
	private final Map<String, String> tenants = new HashMap<>();
	private final Map<String, Mono<ClientRegistration>> clients = new HashMap<>();

	public TenantClientRegistrationRepository() {
		this.tenants.put("master", "http://idp:9999/auth/realms/master");
		this.tenants.put("one", "http://idp:9999/auth/realms/one");
		this.tenants.put("two", "http://idp:9999/auth/realms/two");
		this.tenants.put("three", "http://idp:9999/auth/realms/three");
		this.tenants.put("four", "http://idp:9999/auth/realms/four");
	}

	@Override
	public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
		return this.clients.computeIfAbsent(registrationId, this::fromTenant);
	}

	private Mono<ClientRegistration> fromTenant(String registrationId) {
		return Optional.ofNullable(this.tenants.get(registrationId))
				.map(uri -> Mono.defer(() -> clientRegistration(uri, registrationId)).cache())
				.orElse(Mono.error(new IllegalArgumentException("unknown tenant")));
	}

	private Mono<ClientRegistration> clientRegistration(String uri, String registrationId) {
		log.debug("Client registration : uri = "+uri+", regId = "+registrationId);
		return Mono.just(ClientRegistrations.fromIssuerLocation(uri)
				.registrationId(registrationId)
				.clientId("message")
				.clientSecret("bfbd9f62-02ce-4638-a370-80d45514bd0a")
				.clientAuthenticationMethod(new ClientAuthenticationMethod("jwt"))
				.userNameAttributeName("email")
				.scope("openid", "message:read")
				.build());
	}

	@KafkaListener(topics="tenants")
	public void action(Map<String, Map<String, Object>> action) {
		if (action.containsKey("created")) {
			Map<String, Object> tenant = action.get("created");
			String alias = (String) tenant.get("alias");
			String issuerUri = (String) tenant.get("issuerUri");
			this.tenants.put(alias, issuerUri);
			this.clients.remove(alias);
		}
	}
}
