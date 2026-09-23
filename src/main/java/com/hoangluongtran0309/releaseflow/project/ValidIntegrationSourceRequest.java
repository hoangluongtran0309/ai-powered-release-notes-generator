package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Each provider needs its own fields, so the request is checked as a whole. */
@Documented
@Constraint(validatedBy = IntegrationSourceRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIntegrationSourceRequest {

    String message() default "The source is missing a required field.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
