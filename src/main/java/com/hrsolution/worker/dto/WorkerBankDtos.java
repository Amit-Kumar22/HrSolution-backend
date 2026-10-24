package com.hrsolution.worker.dto;

import com.hrsolution.common.validation.IndianFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for a worker's bank accounts.
 *
 * <p>The account number is never returned in full. Phase 8's bank transfer file
 * is generated server-side from the decrypted value; nothing needs to send it
 * back to a browser, so no endpoint does.
 */
public final class WorkerBankDtos {

    private WorkerBankDtos() {
    }

    @Schema(name = "WorkerBankAccount",
            description = "A worker's bank account. The number is always masked.")
    public record Response(
            Long id,
            Long workerId,
            @Schema(description = "Masked. There is no endpoint that returns it in full - the "
                    + "transfer file is built server-side.", example = "XXXXXXXX9012")
            String accountNumberMasked,
            String ifsc,
            String bankName,
            String branch,
            @Schema(description = "The name as the bank holds it, which is not always the name "
                    + "on the worker record")
            String accountHolderName,
            @Schema(description = "Exactly one account per worker is primary")
            boolean primaryAccount,
            boolean active,
            @Schema(description = "\"HDFC Bank, Bhosari — XXXXXXXX9012\"")
            String displayLabel) {
    }

    @Schema(name = "WorkerBankAccountRequest", description = "Add or update a bank account")
    public record Request(

            @NotBlank(message = "An account number is required")
            @Pattern(regexp = IndianFormats.BANK_ACCOUNT,
                    message = IndianFormats.BANK_ACCOUNT_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Encrypted at rest. Registering the same account twice for "
                            + "one worker is rejected with 409.",
                    example = "123456789012")
            String accountNumber,

            @NotBlank(message = "An IFSC is required")
            @Pattern(regexp = IndianFormats.IFSC, message = IndianFormats.IFSC_MESSAGE)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Not encrypted: it identifies a branch, is published by the "
                            + "RBI, and is useless without the account number.",
                    example = "HDFC0001234")
            String ifsc,

            @Size(max = 120)
            @Schema(example = "HDFC Bank")
            String bankName,

            @Size(max = 120)
            @Schema(example = "Bhosari, Pune")
            String branch,

            @Size(max = 160)
            @Schema(description = "Defaults to the worker's own name when omitted. Set it when "
                    + "the bank holds a different spelling - a mismatch makes the transfer "
                    + "file fail.",
                    example = "Rakesh Vitthal Shinde")
            String accountHolderName,

            @Schema(description = "Makes this the account wages are paid into, demoting any "
                    + "existing primary.", defaultValue = "false")
            Boolean primaryAccount) {
    }
}
