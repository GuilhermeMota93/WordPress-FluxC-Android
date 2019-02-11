package org.wordpress.android.fluxc.network.rest.wpcom.wc.product

import com.google.gson.JsonElement
import org.wordpress.android.fluxc.network.Response

@Suppress("PropertyName")
class ProductApiResponse : Response {
    val id: Long? = null
    var localSiteId = 0
    var name: String? = null
    var slug: String? = null
    var permalink: String? = null

    var date_created: String? = null
    var date_created_gmt: String? = null
    var date_modified: String? = null
    var date_modified_gmt: String? = null

    var type: String? = null
    var status: String? = null
    var featured = false
    var catalog_visibility: String? = null
    var description: String? = null
    var short_description: String? = null
    var sku: String? = null

    var price: String? = null
    var price_html: String? = null
    var regular_price: String? = null
    var sale_price: String? = null
    var date_on_sale_from: String? = null
    var date_on_sale_from_gmt: String? = null
    var date_on_sale_to: String? = null
    var date_on_sale_to_gmt: String? = null
    var on_sale = false
    var total_sales = 0

    var virtual = false
    var downloadable = false
    var downloads: JsonElement? = null
    var download_limit = -1
    var download_expiry = -1

    var external_url: String? = null
    var button_text: String? = null

    var tax_status: String? = null
    var tax_class: String? = null

    var manage_stock = false
    var stock_quantity = 0
    var stock_status: String? = null

    var backorders: String? = null
    var backorders_allowed = false
    var backordered = false

    var sold_individually = false
    var weight: String? = null
    var dimensions: JsonElement? = null

    var shipping_required = false
    var shipping_taxable = false
    var shipping_class: String? = null
    var shipping_class_id = 0

    var reviews_allowed = true
    var average_rating: String? = null
    var rating_count = 0

    var related_ids: JsonElement? = null
    var upsell_ids: JsonElement? = null
    var cross_sell_ids: JsonElement? = null

    var parent_id = 0
    var purchase_note: String? = null
    var menu_order = 0

    var categories: JsonElement? = null
    var tags: JsonElement? = null
    var images: JsonElement? = null
    var attributes: JsonElement? = null
    var default_attributes: JsonElement? = null
    var variations: JsonElement? = null
    var grouped_products: JsonElement? = null
    var meta_data: JsonElement? = null
}
