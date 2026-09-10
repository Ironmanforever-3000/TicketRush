package com.ticketrush.validation;

import com.ticketrush.show.dto.ShowCreateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidShowDatesValidator implements ConstraintValidator<ValidShowDates, ShowCreateRequest> {
    @Override
    public boolean isValid(ShowCreateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startsAt() == null || request.saleOpensAt() == null) {
            return true; // Let @NotNull handle this
        }

        boolean valid = true;

        if (request.saleOpensAt().isAfter(request.startsAt())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("saleOpensAt must be before startsAt")
                   .addPropertyNode("saleOpensAt")
                   .addConstraintViolation();
            valid = false;
        }

        if (request.saleClosesAt() != null && request.saleClosesAt().isAfter(request.startsAt())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("saleClosesAt must be before startsAt")
                   .addPropertyNode("saleClosesAt")
                   .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}
