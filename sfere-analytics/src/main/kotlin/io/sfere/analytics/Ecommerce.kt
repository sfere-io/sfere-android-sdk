package io.sfere.analytics

import io.sfere.analytics.internal.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One product line in `items[]`, with GA4 field names (see EVENTS.md). GA4 needs [itemId] or [itemName]. */
public class SfereItem @JvmOverloads constructor(
    public val itemId: String? = null,
    public val itemName: String? = null,
    public val price: Double? = null,
    public val quantity: Int? = null,
    public val itemBrand: String? = null,
    public val itemCategory: String? = null,
    public val itemCategory2: String? = null,
    public val itemCategory3: String? = null,
    public val itemCategory4: String? = null,
    public val itemCategory5: String? = null,
    public val itemVariant: String? = null,
    public val itemListId: String? = null,
    public val itemListName: String? = null,
    public val index: Int? = null,
    public val coupon: String? = null,
    public val discount: Double? = null,
    public val affiliation: String? = null,
    public val locationId: String? = null,
    /** Extra keys (e.g. `image_url`), forwarded unchanged. */
    public val extra: Map<String, Any?> = emptyMap(),
) {
    public fun toJson(): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        fun put(key: String, value: Any?) {
            if (value != null) fields[key] = value.toJsonElement()
        }
        put("item_id", itemId)
        put("item_name", itemName)
        put("price", price)
        put("quantity", quantity)
        put("item_brand", itemBrand)
        put("item_category", itemCategory)
        put("item_category2", itemCategory2)
        put("item_category3", itemCategory3)
        put("item_category4", itemCategory4)
        put("item_category5", itemCategory5)
        put("item_variant", itemVariant)
        put("item_list_id", itemListId)
        put("item_list_name", itemListName)
        put("index", index)
        put("coupon", coupon)
        put("discount", discount)
        put("affiliation", affiliation)
        put("location_id", locationId)
        for ((key, value) in extra) if (key !in fields) put(key, value)
        return JsonObject(fields)
    }
}

/** The GA4 `purchase` event's properties. Set [dedupId] to the order id when your server also sends it. */
public class SferePurchase @JvmOverloads constructor(
    public val transactionId: String,
    public val currency: String,
    public val value: Double,
    public val items: List<SfereItem>,
    public val coupon: String? = null,
    public val tax: Double? = null,
    public val shipping: Double? = null,
    public val dedupId: String? = null,
    public val extra: Map<String, Any?> = emptyMap(),
) {
    public fun toJson(): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        fields["transaction_id"] = JsonPrimitive(transactionId)
        fields["currency"] = JsonPrimitive(currency)
        fields["value"] = JsonPrimitive(value)
        coupon?.let { fields["coupon"] = JsonPrimitive(it) }
        tax?.let { fields["tax"] = JsonPrimitive(it) }
        shipping?.let { fields["shipping"] = JsonPrimitive(it) }
        dedupId?.let { fields["sfere_dedup_id"] = JsonPrimitive(it) }
        fields["items"] = kotlinx.serialization.json.JsonArray(items.map { it.toJson() })
        for ((key, v) in extra) if (key !in fields) fields[key] = v.toJsonElement()
        return JsonObject(fields)
    }
}
