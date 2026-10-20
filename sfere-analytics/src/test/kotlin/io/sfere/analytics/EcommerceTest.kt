package io.sfere.analytics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EcommerceTest {
    @Test
    fun purchaseSerialisesToGa4Keys() {
        val purchase = SferePurchase(
            transactionId = "order_98765",
            currency = "SAR",
            value = 256.96,
            tax = 16.99,
            shipping = 10.0,
            coupon = "SUMMER20",
            dedupId = "order_98765",
            items = listOf(
                SfereItem(itemId = "sku_12345", itemName = "Away Jersey", price = 89.99, quantity = 2, coupon = "SUMMER20"),
                SfereItem(
                    itemId = "sku_67890", itemName = "Home Shorts", price = 49.99, quantity = 1, itemBrand = "Al-Hilal",
                    itemCategory = "Apparel", itemCategory2 = "Shorts", itemVariant = "XL", itemListId = "related",
                    itemListName = "Related products", index = 3, discount = 10.0, affiliation = "Riyadh store",
                    locationId = "ChIJ", extra = mapOf("image_url" to "https://cdn/x.png"),
                ),
            ),
        )
        val expected = Json.parseToJsonElement(
            """
            {"transaction_id":"order_98765","currency":"SAR","value":256.96,"coupon":"SUMMER20","tax":16.99,
             "shipping":10.0,"sfere_dedup_id":"order_98765","items":[
              {"item_id":"sku_12345","item_name":"Away Jersey","price":89.99,"quantity":2,"coupon":"SUMMER20"},
              {"item_id":"sku_67890","item_name":"Home Shorts","price":49.99,"quantity":1,"item_brand":"Al-Hilal",
               "item_category":"Apparel","item_category2":"Shorts","item_variant":"XL","item_list_id":"related",
               "item_list_name":"Related products","index":3,"discount":10.0,"affiliation":"Riyadh store",
               "location_id":"ChIJ","image_url":"https://cdn/x.png"}]}
            """,
        ).jsonObject
        assertEquals(expected, purchase.toJson())
    }

    @Test
    fun extraKeysNeverOverrideGa4FieldsAndNullsAreLeftOut() {
        val item = SfereItem(itemId = "sku_1", extra = mapOf("item_id" to "other", "note" to null, "size" to "XL"))
        assertEquals(Json.parseToJsonElement("""{"item_id":"sku_1","size":"XL"}"""), item.toJson())
        val purchase = SferePurchase("order_1", "SAR", 10.0, emptyList(), extra = mapOf("currency" to "USD", "channel" to "app"))
        assertEquals(
            Json.parseToJsonElement("""{"transaction_id":"order_1","currency":"SAR","value":10.0,"items":[],"channel":"app"}"""),
            purchase.toJson(),
        )
    }
}
