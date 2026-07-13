#!/usr/bin/env bash
set -euo pipefail

HEC_URL="https://localhost:8092/services/collector/event"
HEC_TOKEN="00000000-0000-0000-0000-000000000000"
CORRELATION_ID="corr-abc-123"

send_event() {
    local level="$1"
    local message="$2"
    local correlation_id="${3:-}"
    curl -sk "$HEC_URL" \
        -H "Authorization: Splunk $HEC_TOKEN" \
        -d "{\"event\": {\"service\": \"checkout-service\", \"level\": \"$level\", \"message\": \"$message\", \"correlationId\": \"$correlation_id\"}}" \
        > /dev/null
}

echo "Seeding checkout-service logs into Splunk..."

for i in $(seq 1 40); do
    send_event "INFO" "Checkout completed successfully for order $i" "corr-ok-$i"
done

send_event "ERROR" "java.lang.NullPointerException: Cannot invoke \\\"String.length()\\\" because \\\"discountCode\\\" is null at com.example.checkout.DiscountService.apply(DiscountService.java:42)" "$CORRELATION_ID"
send_event "ERROR" "at com.example.checkout.CheckoutController.checkout(CheckoutController.java:58)" "$CORRELATION_ID"
send_event "INFO" "Checkout request received for order 9001" "$CORRELATION_ID"

for i in $(seq 1 5); do
    send_event "INFO" "Health check OK"
done

echo "Done. Seeded ~50 events for checkout-service."
