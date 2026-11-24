import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Orders by id, in the order they were placed. */
class OrderRepository {
    private final Map<String, OrderRecord> records = new LinkedHashMap<>();

    boolean exists(String orderId) {
        return records.containsKey(orderId);
    }

    void save(OrderRecord record) {
        records.put(record.order.id(), record);
    }

    Optional<OrderRecord> find(String orderId) {
        return Optional.ofNullable(records.get(orderId));
    }

    List<OrderRecord> withStatus(OrderStatus status) {
        List<OrderRecord> matching = new ArrayList<>();
        for (OrderRecord record : records.values()) {
            if (record.status() == status) {
                matching.add(record);
            }
        }
        return matching;
    }

    int confirmedUnits(String sku) {
        int units = 0;
        for (OrderRecord record : withStatus(OrderStatus.CONFIRMED)) {
            if (record.order.sku().equals(sku)) {
                units += record.order.quantity();
            }
        }
        return units;
    }
}
