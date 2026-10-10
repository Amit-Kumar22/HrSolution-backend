package com.hrsolution.client.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTest {

    private Client client(String legalName, String tradeName) {
        Client client = new Client();
        client.setLegalName(legalName);
        client.setTradeName(tradeName);
        client.setStatus(ClientStatus.ACTIVE);
        return client;
    }

    @Test
    @DisplayName("the client code is derived from the id and zero-padded")
    void clientCodeDerivedFromId() {
        Client client = client("Bharat Textiles Private Limited", null);

        // Null before the first flush - a derived code cannot exist without an
        // id, which is why invoice numbers get a real table in Phase 9 instead.
        assertThat(client.clientCode()).isNull();

        client.setId(42L);
        assertThat(client.clientCode()).isEqualTo("CLI-00042");

        client.setId(123456L);
        assertThat(client.clientCode()).isEqualTo("CLI-123456");
    }

    @Test
    @DisplayName("display name prefers the trade name")
    void displayNamePrefersTradeName() {
        assertThat(client("Bharat Textiles Private Limited", "Bharat Textiles").displayName())
                .isEqualTo("Bharat Textiles");
        assertThat(client("Bharat Textiles Private Limited", null).displayName())
                .isEqualTo("Bharat Textiles Private Limited");
        assertThat(client("Bharat Textiles Private Limited", "  ").displayName())
                .isEqualTo("Bharat Textiles Private Limited");
    }

    @Test
    @DisplayName("only an ACTIVE, undeleted client may take on new work")
    void onlyActiveAllowsNewWork() {
        Client client = client("Test Co", null);
        assertThat(client.allowsNewWork()).isTrue();

        for (ClientStatus status : ClientStatus.values()) {
            client.setStatus(status);
            assertThat(client.allowsNewWork())
                    .as("status %s", status)
                    .isEqualTo(status == ClientStatus.ACTIVE);
        }

        // Soft-deleted blocks new work whatever the status says.
        client.setStatus(ClientStatus.ACTIVE);
        client.markDeleted("someone@example.com");
        assertThat(client.allowsNewWork()).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED stops new work but is not the same as INACTIVE")
    void suspendedSemantics() {
        // Deliberate: a suspended client still has to be invoiced and chased
        // for work already done, so suspension blocks only NEW commitments.
        assertThat(ClientStatus.SUSPENDED.allowsNewWork()).isFalse();
        assertThat(ClientStatus.ACTIVE.allowsNewWork()).isTrue();
        assertThat(ClientStatus.PENDING_APPROVAL.allowsNewWork()).isFalse();
        assertThat(ClientStatus.REJECTED.allowsNewWork()).isFalse();
        assertThat(ClientStatus.INACTIVE.allowsNewWork()).isFalse();
    }
}
