package io.sfere.sample

import io.sfere.analytics.SfereItem
import io.sfere.analytics.SferePurchase

/**
 * The events the sample app's buttons send, with GA4 properties as in EVENTS.md.
 * The iOS sample app (ios/Example) sends the same list.
 */
object SampleEvents {
    private val jersey = SfereItem(itemId = "SKU-JERSEY-HOME", itemName = "Home jersey", price = 299.0, quantity = 1, itemCategory = "apparel")
    private val scarf = SfereItem(itemId = "SKU-SCARF", itemName = "Club scarf", price = 79.0, quantity = 2, itemCategory = "accessories")

    private fun itemsPayload(vararg items: SfereItem): Map<String, Any?> {
        val value = items.sumOf { (it.price ?: 0.0) * (it.quantity ?: 1) }
        return mapOf("currency" to "SAR", "value" to value, "items" to items.map { it.toJson() })
    }

    /** Name and properties for every `track` button, in screen order. */
    val trackEvents: List<Pair<String, Map<String, Any?>>> = listOf(
        "view_item_list" to mapOf("item_list_id" to "new_season", "item_list_name" to "New season", "items" to listOf(jersey.toJson(), scarf.toJson())),
        "select_item" to mapOf("item_list_id" to "new_season", "items" to listOf(jersey.toJson())),
        "view_item" to itemsPayload(jersey),
        "add_to_wishlist" to itemsPayload(scarf),
        "add_to_cart" to itemsPayload(jersey),
        "remove_from_cart" to itemsPayload(jersey),
        "view_cart" to itemsPayload(jersey, scarf),
        "begin_checkout" to itemsPayload(jersey, scarf),
        "add_shipping_info" to itemsPayload(jersey, scarf) + ("shipping_tier" to "standard"),
        "add_payment_info" to itemsPayload(jersey, scarf) + ("payment_type" to "card"),
        "refund" to itemsPayload(jersey) + ("transaction_id" to "T-SAMPLE-1"),
        "search" to mapOf("search_term" to "home jersey"),
        "sign_up" to mapOf("method" to "email"),
        "login" to mapOf("method" to "email"),
        "generate_lead" to mapOf("currency" to "SAR", "value" to 50.0),
        "match_ticket_scanned" to mapOf("match_id" to "42", "gate" to "B"),
    )

    fun purchase(): SferePurchase = SferePurchase(
        transactionId = "T-SAMPLE-${System.currentTimeMillis()}",
        currency = "SAR",
        value = 457.0,
        items = listOf(jersey, scarf),
        shipping = 20.0,
    )

    const val TEST_DEEP_LINK = "sfereexample://match/42?utm_source=sample_app&utm_campaign=sdk_check&gclid=sample-gclid"
}
