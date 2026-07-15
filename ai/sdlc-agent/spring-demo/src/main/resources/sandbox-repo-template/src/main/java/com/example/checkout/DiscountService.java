package com.example.checkout;

import java.math.BigDecimal;

public class DiscountService {

    public BigDecimal apply(BigDecimal price, String discountCode) {
        if (discountCode.length() > 0) {
            return price.multiply(BigDecimal.valueOf(0.9));
        }
        return price;
    }
}
