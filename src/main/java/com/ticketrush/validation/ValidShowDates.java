package com.ticketrush.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidShowDatesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidShowDates {
    String message() default "Invalid show dates: saleOpensAt must be before startsAt, and saleClosesAt must be before startsAt";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
