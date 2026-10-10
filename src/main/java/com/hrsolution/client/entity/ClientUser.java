package com.hrsolution.client.entity;

import com.hrsolution.common.domain.BaseEntity;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Links a CLIENT-role login to the company it belongs to.
 *
 * <p><strong>The table every ownership check resolves through.</strong> When a
 * client user asks for "my invoices" or "my deployed workers", the client id
 * comes from here - never from a request parameter, which the caller controls.
 *
 * <p>{@code user_id} is unique: one login belongs to exactly one client.
 * Without that, a mis-assigned account could read two companies' data, and
 * every ownership check would have to cope with a set of client ids rather than
 * one.
 */
@Entity
@Table(name = "client_users")
@Getter
@Setter
public class ClientUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "designation", length = 120)
    private String designation;

    /**
     * Who receives contract and invoice notifications.
     *
     * <p>At most one per client, enforced in {@code ClientService} rather than
     * the schema - MySQL cannot express "unique where primary_contact = true".
     */
    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact = false;

    /**
     * False revokes this person's access to the client's data while keeping the
     * link, so the audit trail still explains who saw what.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
