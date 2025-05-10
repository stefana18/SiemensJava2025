package com.siemens.internship.validation;

import com.siemens.internship.model.Item;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

public class ItemValidator {

    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = factory.getValidator();

    public static Set<ConstraintViolation<Item>> validate(Item item) {
        return validator.validate(item);
    }
}
