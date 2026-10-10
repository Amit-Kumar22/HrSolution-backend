package com.hrsolution.enquiry.dto;

import com.hrsolution.common.validation.IndianFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response shapes for the general Contact Us form. */
public final class ContactMessageDtos {

    private ContactMessageDtos() {
    }

    @Schema(name = "ContactMessageSubmitRequest", description = "Public Contact Us submission")
    public record SubmitRequest(

            @NotBlank(message = "Name is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Asha Patil")
            String name,

            // Required here, unlike on the enquiry form: a general question has
            // no phone follow-up implied, so email is the only way to reply.
            @NotBlank(message = "Email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 180)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "asha@example.com")
            String email,

            @Pattern(regexp = IndianFormats.MOBILE, message = IndianFormats.MOBILE_MESSAGE)
            @Schema(example = "9876543210")
            String phone,

            @Size(max = 200)
            @Schema(example = "Question about payroll outsourcing")
            String subject,

            @NotBlank(message = "Message is required")
            @Size(max = 2000)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String message,

            @Schema(description = "Honeypot. Leave absent or empty.", example = "")
            String website,

            @Schema(description = "Epoch millis when the form was rendered. Optional.")
            Long formRenderedAt) {
    }

    @Schema(name = "ContactMessage", description = "A contact message, for staff")
    public record Response(
            Long id,
            String name,
            String email,
            String phone,
            String subject,
            String message,
            boolean read,
            Instant readAt,
            boolean replied,
            boolean spam,
            Instant createdAt) {
    }
}
