package com.hrsolution.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts a String column on write and decrypts it on read.
 *
 * <p>Apply it explicitly with
 * {@code @Convert(converter = EncryptedString.class)} rather than
 * {@code autoApply = true}. Auto-applying would encrypt <em>every</em> String
 * column in the schema, which is both wrong and unrecoverable once written.
 *
 * <p>A {@code @Component} so Spring injects {@link FieldCrypto}: Spring Boot
 * registers its own bean container with Hibernate, so a converter that is also
 * a bean is resolved through it rather than being instantiated reflectively
 * with a no-arg constructor. {@code EncryptedStringConverterIT} asserts that
 * wiring actually happens, because the failure mode if it ever stops is a
 * {@code NullPointerException} on the first write to a worker.
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class EncryptedString implements AttributeConverter<String, String> {

    private final FieldCrypto fieldCrypto;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldCrypto.decrypt(dbData);
    }
}
