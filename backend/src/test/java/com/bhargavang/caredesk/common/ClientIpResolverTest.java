package com.bhargavang.caredesk.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.bhargavang.caredesk.config.CareDeskProperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	@Test
	void withNoProxyTheConnectingAddressIsUsedAndForwardedForIsIgnored() {
		MockHttpServletRequest request = request("198.51.100.7", "203.0.113.1");
		assertThat(resolver(0, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void oneTrustedProxyMeansTheLastEntryIsTheClient() {
		// The client claimed 203.0.113.1; the proxy appended the address it really saw.
		MockHttpServletRequest request = request("10.0.0.5", "203.0.113.1, 198.51.100.7");
		assertThat(resolver(1, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void twoTrustedProxiesSkipTheInnerOne() {
		MockHttpServletRequest request = request("10.0.0.5", "203.0.113.1, 198.51.100.7, 192.0.2.10");
		assertThat(resolver(2, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void entriesAcrossSeveralHeadersCountAsOneChain() {
		MockHttpServletRequest request = request("10.0.0.5", "203.0.113.1");
		request.addHeader("X-Forwarded-For", "198.51.100.7");
		assertThat(resolver(1, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void aShortChainFallsBackToTheFurthestEntry() {
		MockHttpServletRequest request = request("10.0.0.5", "198.51.100.7");
		assertThat(resolver(3, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void missingForwardedForFallsBackToTheConnectingAddress() {
		MockHttpServletRequest request = request("198.51.100.7", null);
		assertThat(resolver(1, "").resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void aConfiguredEdgeHeaderWinsWhenPresent() {
		MockHttpServletRequest request = request("10.0.0.5", "203.0.113.1, 192.0.2.10");
		request.addHeader("CF-Connecting-IP", "198.51.100.7");
		assertThat(resolver(1, "CF-Connecting-IP").resolve(request)).isEqualTo("198.51.100.7");

		MockHttpServletRequest without = request("10.0.0.5", "203.0.113.1, 192.0.2.10");
		assertThat(resolver(1, "CF-Connecting-IP").resolve(without)).isEqualTo("192.0.2.10");
	}

	@Test
	void absurdlyLongValuesAreCapped() {
		MockHttpServletRequest request = request("10.0.0.5", "x".repeat(5000));
		assertThat(resolver(1, "").resolve(request)).hasSize(64);
	}

	private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}

	private static ClientIpResolver resolver(int trustedHops, String header) {
		CareDeskProperties properties = new CareDeskProperties(null, null, null, null, null, null,
				new CareDeskProperties.ClientIp(trustedHops, header, false), null);
		return new ClientIpResolver(properties);
	}
}
