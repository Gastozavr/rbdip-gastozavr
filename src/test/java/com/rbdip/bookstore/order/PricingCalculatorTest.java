package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.PricingCalculator.LineItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Характеризационные тесты расчёта цены (ЛР2). Ожидаемые значения сняты
 * с исходной реализации до рефакторинга и фиксируют её поведение, включая
 * граничные случаи порогов и порядок применения скидок.
 */
class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    private BigDecimal total(String customerType, String coupon, LineItem... items) {
        return calculator.calculateOrderTotal(List.of(items), customerType, coupon);
    }

    private static LineItem item(String price, int quantity) {
        return new LineItem(new BigDecimal(price), quantity);
    }

    @Nested
    class BasePrice {

        @Test
        void emptyOrderCostsZero() {
            assertThat(calculator.calculateOrderTotal(List.of(), "regular", null)).isEqualTo("0.00");
        }

        @Test
        void sumsAllLines() {
            assertThat(total("regular", null, item("5.00", 1), item("2.50", 4))).isEqualTo("15.00");
        }

        @Test
        void alwaysReturnsTwoDecimalPlaces() {
            assertThat(total("regular", null, item("7", 3))).isEqualTo("21.00").hasScaleOf(2);
        }

        @Test
        void roundsHalfUp() {
            assertThat(total("regular", null, item("0.005", 1))).isEqualTo("0.01");
            assertThat(total("regular", null, item("0.004", 1))).isEqualTo("0.00");
        }
    }

    @Nested
    class BulkDiscount {

        @Test
        void noDiscountAtThreshold() {
            assertThat(total("regular", null, item("10.00", 10))).isEqualTo("100.00");
        }

        @Test
        void fivePercentOffAboveThreshold() {
            assertThat(total("regular", null, item("10.00", 11))).isEqualTo("104.50");
        }

        @Test
        void appliesPerLineNotPerOrder() {
            // 6 + 6 штук одного товара в разных строках - скидки нет ни на одной.
            assertThat(total("regular", null, item("10.00", 6), item("10.00", 6))).isEqualTo("120.00");
            assertThat(total("regular", null, item("10.00", 11), item("1.00", 1))).isEqualTo("105.50");
        }
    }

    @Nested
    class CustomerDiscount {

        @ParameterizedTest
        @CsvSource({"vip, 90.00", "wholesale, 85.00", "regular, 100.00", "student, 100.00", "VIP, 100.00"})
        void dependsOnCustomerType(String customerType, String expected) {
            assertThat(total(customerType, null, item("100.00", 1))).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullSource
        void missingCustomerTypeMeansNoDiscount(String customerType) {
            assertThat(total(customerType, null, item("100.00", 1))).isEqualTo("100.00");
        }
    }

    @Nested
    class Coupons {

        @Test
        void save10SubtractsTenFromTotal() {
            assertThat(total("regular", "SAVE10", item("100.00", 1))).isEqualTo("90.00");
        }

        @Test
        void save20PercentTakesTwentyPercentOff() {
            assertThat(total("regular", "SAVE20PERCENT", item("100.00", 1))).isEqualTo("80.00");
        }

        @ParameterizedTest
        @ValueSource(strings = {"SAVE99", "save10", ""})
        void unknownCouponIsIgnored(String coupon) {
            assertThat(total("regular", coupon, item("100.00", 1))).isEqualTo("100.00");
        }

        @Test
        void missingCouponIsIgnored() {
            assertThat(total("regular", null, item("100.00", 1))).isEqualTo("100.00");
        }

        @Test
        void totalNeverGoesBelowZero() {
            assertThat(total("regular", "SAVE10", item("5.00", 1))).isEqualTo("0.00");
        }

        @Test
        void couponEqualToTotalGivesZero() {
            assertThat(total("regular", "SAVE10", item("10.00", 1))).isEqualTo("0.00");
        }
    }

    @Nested
    class LargeOrderDiscount {

        @Test
        void noDiscountAtExactlyThreshold() {
            assertThat(total("regular", null, item("1000.00", 1))).isEqualTo("1000.00");
        }

        @Test
        void twoPercentOffAboveThreshold() {
            assertThat(total("regular", null, item("1000.01", 1))).isEqualTo("980.01");
            assertThat(total("regular", null, item("1000.00", 2))).isEqualTo("1960.00");
        }
    }

    @Nested
    class DiscountOrder {

        @Test
        void customerDiscountBeforeFixedCoupon() {
            // (100 * 0.9) - 10 = 80; в обратном порядке было бы 81.
            assertThat(total("vip", "SAVE10", item("100.00", 1))).isEqualTo("80.00");
        }

        @Test
        void combinesCustomerAndPercentCoupon() {
            assertThat(total("wholesale", "SAVE20PERCENT", item("100.00", 1))).isEqualTo("68.00");
        }

        @Test
        void bulkThenCustomerThenCoupon() {
            // 20 * 11 = 220 -> *0.95 = 209 -> *0.9 = 188.1 -> -10 = 178.10
            assertThat(total("vip", "SAVE10", item("20.00", 11))).isEqualTo("178.10");
        }

        @Test
        void largeOrderThresholdCheckedAfterCoupon() {
            // 1005 - 10 = 995 - уже не крупный заказ.
            assertThat(total("regular", "SAVE10", item("1005.00", 1))).isEqualTo("995.00");
            // 1050 - 10 = 1040 -> *0.98 = 1019.20
            assertThat(total("regular", "SAVE10", item("1050.00", 1))).isEqualTo("1019.20");
        }

        @Test
        void largeOrderThresholdCheckedAfterCustomerDiscount() {
            // 1100 * 0.9 = 990 - уже не крупный заказ.
            assertThat(total("vip", null, item("1100.00", 1))).isEqualTo("990.00");
        }

        @Test
        void bulkDiscountCountsTowardsLargeOrder() {
            // 100 * 11 = 1100 -> *0.95 = 1045 -> *0.98 = 1024.10
            assertThat(total("regular", null, item("100.00", 11))).isEqualTo("1024.10");
        }
    }
}
