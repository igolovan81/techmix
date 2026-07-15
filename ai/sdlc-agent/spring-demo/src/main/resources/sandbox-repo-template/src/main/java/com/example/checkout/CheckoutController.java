package com.example.checkout;

import java.math.BigDecimal;

public class CheckoutController {

    private final DiscountService discountService;

    public CheckoutController(DiscountService discountService) {
        this.discountService = discountService;
    }

    public BigDecimal checkout(BigDecimal price, String discountCode) {
        return discountService.apply(price, discountCode);
    }
}
