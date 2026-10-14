package com.hrsolution.recruitment.entity;

/** How an interview is conducted. */
public enum InterviewMode {

    IN_PERSON,

    PHONE,

    VIDEO;

    /**
     * Whether {@code venueOrLink} should hold a URL rather than an address.
     *
     * <p>One column serves both because exactly one applies and which it is
     * follows from the mode - but the validation message has to differ, hence
     * this.
     */
    public boolean needsALink() {
        return this == VIDEO;
    }
}
