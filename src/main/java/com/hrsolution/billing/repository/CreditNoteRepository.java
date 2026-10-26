package com.hrsolution.billing.repository;

import com.hrsolution.billing.entity.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findByInvoiceId(Long invoiceId);
}
