/** What the customer asked for. */
record Order(String id, String sku, int quantity, long cents) {
    Order {
        if (id == null || id.isBlank() || sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("order needs an id and a sku");
        }
        if (quantity <= 0 || cents <= 0) {
            throw new IllegalArgumentException("quantity and price must be positive");
        }
    }
}
