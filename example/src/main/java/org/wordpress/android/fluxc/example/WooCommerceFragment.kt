package org.wordpress.android.fluxc.example

import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.fragment_woocommerce.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCOrderAction.FETCH_ORDERS
import org.wordpress.android.fluxc.action.WCOrderAction.UPDATE_ORDER_STATUS
import org.wordpress.android.fluxc.example.utils.showSingleLineDialog
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.FetchOrdersPayload
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import org.wordpress.android.fluxc.store.WCOrderStore.UpdateOrderStatusPayload
import org.wordpress.android.fluxc.store.WooCommerceStore
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject

class WooCommerceFragment : Fragment() {
    @Inject internal lateinit var dispatcher: Dispatcher
    @Inject internal lateinit var wooCommerceStore: WooCommerceStore
    @Inject internal lateinit var wcOrderStore: WCOrderStore

    override fun onAttach(context: Context?) {
        AndroidInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_woocommerce, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        log_sites.setOnClickListener {
            for (site in wooCommerceStore.getWooCommerceSites()) {
                prependToLog(site.name + ": " + if (site.isWpComStore) "WP.com store" else "Self-hosted store")
                AppLog.i(T.API, LogUtils.toString(site))
            }
        }

        fetch_orders.setOnClickListener {
            getFirstWCSite()?.let {
                val payload = FetchOrdersPayload(it, loadMore = false)
                dispatcher.dispatch(WCOrderActionBuilder.newFetchOrdersAction(payload))
            } ?: showNoWCSitesToast()
        }

        update_latest_order_status.setOnClickListener {
            getFirstWCSite()?.let { site ->
                wcOrderStore.getOrdersForSite(site).firstOrNull()?.let { order ->
                    showSingleLineDialog(activity, "Enter new order status", { editText ->
                        val status = editText.text.toString()
                        val payload = UpdateOrderStatusPayload(order, site, status)
                        dispatcher.dispatch(WCOrderActionBuilder.newUpdateOrderStatusAction(payload))
                    })
                } ?: run { showNoOrdersToast(site) }
            } ?: showNoWCSitesToast()
        }
    }

    override fun onStart() {
        super.onStart()
        dispatcher.register(this)
    }

    override fun onStop() {
        super.onStop()
        dispatcher.unregister(this)
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOrderChanged(event: OnOrderChanged) {
        if (event.isError) {
            prependToLog("Error from " + event.causeOfChange + " - error: " + event.error.type)
            return
        }

        getFirstWCSite()?.let { site ->
            wcOrderStore.getOrdersForSite(site).let { orderList ->
                if (orderList.isEmpty()) {
                    prependToLog("No orders were stored for site " + site.name + " =(")
                    return
                }

                when (event.causeOfChange) {
                    FETCH_ORDERS -> prependToLog("Fetched " + event.rowsAffected + " orders from: " + site.name)
                    UPDATE_ORDER_STATUS ->
                        with (orderList[0]) { prependToLog("Updated order status for $number to $status") }
                    else -> prependToLog("Order store was updated from a " + event.causeOfChange)
                }
            }
        }
    }

    private fun getFirstWCSite() = wooCommerceStore.getWooCommerceSites().getOrNull(0)

    private fun prependToLog(s: String) = (activity as MainExampleActivity).prependToLog(s)

    private fun showNoWCSitesToast() {
        ToastUtils.showToast(activity, "No WooCommerce sites found for this account!")
    }

    private fun showNoOrdersToast(site: SiteModel) {
        ToastUtils.showToast(activity, "No orders found for site: " + site.name)
    }
}
