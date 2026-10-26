package com.hrsolution.billing.repository;

import com.hrsolution.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    long countByInvoiceNumberStartingWith(String prefix);
}
